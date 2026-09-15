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

# JDK 17 (what the pom targets), Maven, Python, Racket, and Chromium for
# Puppeteer. The distro Chromium saves us the 170 MB download and the usual
# hunt for missing shared libraries when Chrome won't start in a container.
RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        maven \
        python3 python3-venv python3-pip \
        racket \
        chromium \
        ca-certificates curl git unzip xz-utils \
    && rm -rf /var/lib/apt/lists/*

# Node from the official tarball so we control the version, not the distro.
ARG NODE_VERSION=22.11.0
RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" -o /tmp/node.tar.xz \
    && tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 \
    && rm /tmp/node.tar.xz \
    && node --version && npm --version

ENV PUPPETEER_SKIP_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium

# Not root, mostly because Chromium won't start as root without --no-sandbox
# and the sample tests don't pass it.
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
    && (cd samples/JSProject && npm install --no-fund --no-audit || true) \
    && (cd samples/CSProject && dotnet restore tests/CSProject.Tests/CSProject.Tests.csproj || true)

CMD ["/bin/bash"]
