# Demo image for the multi-language mutation tester. Everything is baked in and
# pre-built, so running it needs no network and no installs on the host.
#
#   docker build -t mutationtester:demo .
#   docker run --rm -it mutationtester:demo
#
# Against your own code, mount it:
#   docker run --rm -it -v "${PWD}/myproject:/work" mutationtester:demo \
#     java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar /work /work/mutation.config
#
# Onto a machine with no registry access:
#   docker save mutationtester:demo | gzip > mutationtester-demo.tar.gz
#   docker load < mutationtester-demo.tar.gz
#
# Starting from Microsoft's image because .NET is the fiddliest piece to get right.

FROM mcr.microsoft.com/dotnet/sdk:10.0

ENV DEBIAN_FRONTEND=noninteractive \
    DOTNET_CLI_TELEMETRY_OPTOUT=1 \
    DOTNET_NOLOGO=1 \
    DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1

# JDK 17 (what the pom targets), Maven, Python and Racket, plus the shared
# libraries Chrome needs. Puppeteer downloads its own Chrome - don't reach for
# the chromium package here, it's snap-only on Ubuntu and apt quietly satisfies
# it with a stub that isn't a browser.
RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        maven \
        python3 python3-venv python3-pip \
        racket \
        ca-certificates curl git unzip xz-utils \
        fonts-liberation libasound2t64 libatk-bridge2.0-0t64 libatk1.0-0t64 \
        libcairo2 libcups2t64 libdbus-1-3 libexpat1 libfontconfig1 libgbm1 \
        libglib2.0-0t64 libgtk-3-0t64 libnspr4 libnss3 libpango-1.0-0 \
        libx11-6 libxcb1 libxcomposite1 libxdamage1 libxext6 libxfixes3 \
        libxi6 libxkbcommon0 libxrandr2 libxtst6 libxss1 \
    && rm -rf /var/lib/apt/lists/*

# Node from the official tarball so we control the version, not the distro.
ARG NODE_VERSION=22.11.0
RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" -o /tmp/node.tar.xz \
    && tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 \
    && rm /tmp/node.tar.xz \
    && node --version && npm --version

ENV PUPPETEER_CACHE_DIR=/home/demo/.cache/puppeteer

# Don't run as root. The JS sample already launches Chrome with --no-sandbox,
# so this is just good hygiene rather than something Puppeteer depends on.
RUN useradd --create-home --shell /bin/bash demo
USER demo

# pytest in a venv that's first on PATH, so `python3 -m pytest` finds it.
RUN python3 -m venv /home/demo/venv && /home/demo/venv/bin/pip install --no-cache-dir pytest
ENV PATH=/home/demo/venv/bin:$PATH

WORKDIR /opt/mutationtester
COPY --chown=demo:demo . .

# Build now and warm every package cache so the demo starts instantly and
# offline. The sample restores are best-effort - one flaky language shouldn't
# fail the whole build.
RUN mvn -B --no-transfer-progress package -DskipTests \
    && (cd samples/MavenProject && mvn -B --no-transfer-progress -DskipTests dependency:go-offline || true) \
    && (cd samples/JSProject && npm install --no-fund --no-audit && npx puppeteer browsers install chrome) \
    && (cd samples/CSProject && dotnet restore tests/CSProject.Tests/CSProject.Tests.csproj || true)

# Fail here rather than during a demo. An earlier version of this file built
# perfectly well with a "Chrome" that was really a snap stub, so actually
# launch the browser instead of trusting that the package installed.
RUN java -version && javac -version && mvn -v >/dev/null \
    && dotnet --list-sdks >/dev/null && node --version \
    && python3 -m pytest --version >/dev/null && racket --version \
    && test -f target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar \
    && node -e "require('/opt/mutationtester/samples/JSProject/node_modules/puppeteer') \
        .launch({args: ['--no-sandbox', '--disable-setuid-sandbox']}) \
        .then(async b => { console.log('chrome ok:', await b.version()); await b.close(); }) \
        .catch(e => { console.error('chrome failed:', e.message); process.exit(1); })"

CMD ["/bin/bash"]
