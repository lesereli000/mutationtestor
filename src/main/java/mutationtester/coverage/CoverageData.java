package mutationtester.coverage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CoverageData {
    private final Map<String, Set<Integer>> coveredLinesByPath = new HashMap<>();

    public static CoverageData empty() {
        return new CoverageData();
    }

    public static CoverageData load(Path reportPath) throws IOException {
        CoverageData data = new CoverageData();
        if (reportPath == null || !Files.exists(reportPath)) {
            return data;
        }

        if (Files.isDirectory(reportPath)) {
            data.loadPythonTraceDirectory(reportPath);
            return data;
        }

        String fileName = reportPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".info") || fileName.endsWith(".lcov")) {
            data.loadLcov(reportPath);
        } else if (fileName.endsWith(".xml")) {
            data.loadXml(reportPath);
        }

        return data;
    }

    public boolean hasCoverage() {
        return !coveredLinesByPath.isEmpty();
    }

    public boolean isLineCovered(Path sourceFile, int lineNumber) {
        if (sourceFile == null || lineNumber <= 0) {
            return false;
        }

        String normalizedSource = normalizePath(sourceFile);
        for (Map.Entry<String, Set<Integer>> entry : coveredLinesByPath.entrySet()) {
            String reportPath = entry.getKey();
            if (pathsMatch(normalizedSource, reportPath) && entry.getValue().contains(lineNumber)) {
                return true;
            }
        }
        return false;
    }

    private void loadLcov(Path reportPath) throws IOException {
        String currentFile = null;
        for (String line : Files.readAllLines(reportPath, StandardCharsets.UTF_8)) {
            if (line.startsWith("SF:")) {
                currentFile = normalizePath(Path.of(line.substring(3).trim()));
            } else if (line.startsWith("DA:") && currentFile != null) {
                String[] parts = line.substring(3).split(",");
                if (parts.length >= 2 && parseInt(parts[1]) > 0) {
                    addCoveredLine(currentFile, parseInt(parts[0]));
                }
            } else if (line.equals("end_of_record")) {
                currentFile = null;
            }
        }
    }

    private void loadPythonTraceDirectory(Path reportDirectory) throws IOException {
        try (Stream<Path> paths = Files.walk(reportDirectory)) {
            for (Path coverFile : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".cover"))
                    .toList()) {
                loadPythonTraceCoverFile(reportDirectory, coverFile);
            }
        }
    }

    private void loadPythonTraceCoverFile(Path reportDirectory, Path coverFile) throws IOException {
        String relativeName = reportDirectory.relativize(coverFile).toString()
                .replace('\\', '/')
                .replaceAll("\\.cover$", "");
        String sourceFile = normalizePath(Path.of(relativeName.replace('.', '/') + ".py"));
        Pattern coveredLinePattern = Pattern.compile("^\\s*(\\d+):");

        int lineNumber = 0;
        for (String line : Files.readAllLines(coverFile, StandardCharsets.UTF_8)) {
            lineNumber++;
            Matcher matcher = coveredLinePattern.matcher(line);
            if (matcher.find() && parseInt(matcher.group(1)) > 0) {
                addCoveredLine(sourceFile, lineNumber);
            }
        }
    }

    private void loadXml(Path reportPath) {
        try {
            DocumentBuilder builder = createCoverageXmlBuilder();
            Document doc = builder.parse(reportPath.toFile());
            doc.getDocumentElement().normalize();
            loadCoberturaXml(doc);
            loadJacocoXml(doc);
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse coverage report " + reportPath + ": " + e.getMessage());
        }
    }

    private DocumentBuilder createCoverageXmlBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private void loadCoberturaXml(Document doc) {
        NodeList classes = doc.getElementsByTagName("class");
        for (int i = 0; i < classes.getLength(); i++) {
            Node node = classes.item(i);
            if (!(node instanceof Element classElement) || !classElement.hasAttribute("filename")) {
                continue;
            }

            String file = normalizePath(Path.of(classElement.getAttribute("filename")));
            NodeList lines = classElement.getElementsByTagName("line");
            for (int j = 0; j < lines.getLength(); j++) {
                Node lineNode = lines.item(j);
                if (!(lineNode instanceof Element lineElement)) {
                    continue;
                }

                int hits = parseInt(lineElement.getAttribute("hits"));
                int number = parseInt(lineElement.getAttribute("number"));
                if (hits > 0 && number > 0) {
                    addCoveredLine(file, number);
                }
            }
        }
    }

    private void loadJacocoXml(Document doc) {
        NodeList packages = doc.getElementsByTagName("package");
        for (int i = 0; i < packages.getLength(); i++) {
            Node packageNode = packages.item(i);
            if (!(packageNode instanceof Element packageElement)) {
                continue;
            }

            String packageName = packageElement.getAttribute("name");
            NodeList sourceFiles = packageElement.getElementsByTagName("sourcefile");
            for (int j = 0; j < sourceFiles.getLength(); j++) {
                Node sourceNode = sourceFiles.item(j);
                if (!(sourceNode instanceof Element sourceElement)) {
                    continue;
                }

                String file = packageName.isEmpty()
                        ? sourceElement.getAttribute("name")
                        : packageName + "/" + sourceElement.getAttribute("name");
                file = normalizePath(Path.of(file));

                NodeList lines = sourceElement.getElementsByTagName("line");
                for (int k = 0; k < lines.getLength(); k++) {
                    Node lineNode = lines.item(k);
                    if (!(lineNode instanceof Element lineElement)) {
                        continue;
                    }

                    int number = parseInt(lineElement.getAttribute("nr"));
                    int instructionCovered = parseInt(lineElement.getAttribute("ci"));
                    int branchCovered = parseInt(lineElement.getAttribute("cb"));
                    if (number > 0 && (instructionCovered > 0 || branchCovered > 0)) {
                        addCoveredLine(file, number);
                    }
                }
            }
        }
    }

    private void addCoveredLine(String file, int lineNumber) {
        coveredLinesByPath.computeIfAbsent(file, ignored -> new HashSet<>()).add(lineNumber);
    }

    private static boolean pathsMatch(String sourcePath, String reportPath) {
        return sourcePath.equals(reportPath)
                || sourcePath.endsWith("/" + reportPath)
                || reportPath.endsWith("/" + sourcePath);
    }

    private static String normalizePath(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
