package mutationtester.report;

import java.util.LinkedHashSet;
import java.util.Set;

import mutationtester.equivalent.EquivalentsBranch;
import mutationtester.equivalent.MiniJson;

/**
 * What the report pages need to show and save equivalent mutants without a
 * server: the IDs the run used (embedded in each page, so a report downloaded
 * from CI is accurate offline) and, in GitHub Actions, the repository to save
 * new marks to.
 */
public record EquivalentsPageData(Set<String> hiddenIds, String source, String repoUrl) {

    /** Pages generated without run data fall back to the local API server, as before. */
    public static EquivalentsPageData empty() {
        return new EquivalentsPageData(new LinkedHashSet<>(), "none", null);
    }

    /**
     * Builds the page data for a run. The repository comes from the variables
     * GitHub Actions sets; outside Actions there is nowhere to save to, so the
     * page offers a download instead.
     */
    public static EquivalentsPageData forRun(EquivalentsBranch.Snapshot snapshot) {
        return forRun(snapshot, System.getenv("GITHUB_SERVER_URL"), System.getenv("GITHUB_REPOSITORY"));
    }

    static EquivalentsPageData forRun(EquivalentsBranch.Snapshot snapshot, String serverUrl, String repository) {
        String repoUrl = null;
        if (repository != null && !repository.isBlank()) {
            String server = serverUrl == null || serverUrl.isBlank() ? "https://github.com" : serverUrl;
            repoUrl = server.replaceAll("/+$", "") + "/" + repository;
        }
        String source = switch (snapshot.source()) {
            case BRANCH -> "branch";
            case LOCAL_FILE -> "local";
            case NONE -> "none";
        };
        return new EquivalentsPageData(snapshot.ids(), source, repoUrl);
    }

    /** The page's data as a JSON script block. */
    public String toDataScript() {
        StringBuilder json = new StringBuilder("{\"hiddenMutantIds\":[");
        int index = 0;
        for (String id : hiddenIds) {
            json.append(index++ > 0 ? "," : "").append(MiniJson.quote(id));
        }
        json.append("],\"source\":").append(MiniJson.quote(source));
        json.append(",\"repoUrl\":").append(repoUrl == null ? "null" : MiniJson.quote(repoUrl));
        json.append(",\"branch\":").append(MiniJson.quote(EquivalentsBranch.BRANCH));
        json.append(",\"pendingDir\":").append(MiniJson.quote(EquivalentsBranch.PENDING_DIR));
        json.append(",\"file\":").append(MiniJson.quote(EquivalentsBranch.FILE));
        json.append("}");

        // "<" is escaped so a source line containing </script> cannot end the block early.
        String safe = json.toString().replace("<", "\\u003c");
        return "<script id=\"equivalents-data\" type=\"application/json\">" + safe + "</script>\n";
    }

    /**
     * Shared helpers: readEquivalentsData() and loadHiddenMutantIds(). The
     * local API server is only consulted when the IDs did not come from the
     * branch and the page is not from GitHub Actions, so a server left running
     * for another project can't change a CI report.
     */
    public static String sharedScript() {
        return """
                <script>
                function readEquivalentsData() {
                    try {
                        return JSON.parse(document.getElementById('equivalents-data').textContent);
                    } catch (e) {
                        return { hiddenMutantIds: [], source: 'none', repoUrl: null };
                    }
                }
                function usesLocalServer(data) {
                    return !data.repoUrl && data.source !== 'branch';
                }
                async function fetchLocalServerIds() {
                    const controller = new AbortController();
                    const timer = setTimeout(() => controller.abort(), 1500);
                    try {
                        const res = await fetch('http://localhost:8081/api/equivalent-mutants', { signal: controller.signal });
                        if (!res.ok) return null;
                        const data = await res.json();
                        return new Set(data.hiddenMutantIds || []);
                    } catch (e) {
                        return null;
                    } finally {
                        clearTimeout(timer);
                    }
                }
                async function loadHiddenMutantIds() {
                    const data = readEquivalentsData();
                    if (usesLocalServer(data)) {
                        const fromServer = await fetchLocalServerIds();
                        if (fromServer) return fromServer;
                    }
                    return new Set(data.hiddenMutantIds || []);
                }
                </script>
                """;
    }
}
