import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EquivalentMutantApiServer {
    private static final Pattern ID_PATTERN = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\"])*)\\\"");

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/api/equivalent-mutants", new EquivalentMutantHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Equivalent mutant API listening on http://localhost:8081/api/equivalent-mutants");
    }

    static class EquivalentMutantHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            String method = exchange.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            try {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGet(exchange);
                    return;
                }
                if ("POST".equalsIgnoreCase(method)) {
                    handlePost(exchange);
                    return;
                }
                if ("DELETE".equalsIgnoreCase(method)) {
                    handleDelete(exchange);
                    return;
                }

                writeJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            } catch (Exception e) {
                writeJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            Set<String> hiddenIds = EquivalentMutantStore.loadHiddenMutantIds();

            StringBuilder json = new StringBuilder();
            json.append("{\"hiddenMutantIds\":[");
            int index = 0;
            for (String id : hiddenIds) {
                if (index++ > 0) {
                    json.append(",");
                }
                json.append("\"").append(escapeJson(id)).append("\"");
            }
            json.append("]}");

            writeJson(exchange, 200, json.toString());
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            String mutantId = extractId(readBody(exchange));
            if (mutantId == null || mutantId.isBlank()) {
                writeJson(exchange, 400, "{\"error\":\"Missing mutant id\"}");
                return;
            }

            EquivalentMutantStore.addHiddenMutantId(mutantId);
            writeJson(exchange, 200, "{\"status\":\"saved\"}");
        }

        private void handleDelete(HttpExchange exchange) throws IOException {
            String mutantId = extractId(readBody(exchange));
            if (mutantId == null || mutantId.isBlank()) {
                writeJson(exchange, 400, "{\"error\":\"Missing mutant id\"}");
                return;
            }

            EquivalentMutantStore.removeHiddenMutantId(mutantId);
            writeJson(exchange, 200, "{\"status\":\"removed\"}");
        }

        private static String readBody(HttpExchange exchange) throws IOException {
            try (InputStream in = exchange.getRequestBody()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private static String extractId(String body) {
            Matcher matcher = ID_PATTERN.matcher(body);
            if (!matcher.find()) {
                return null;
            }
            return matcher.group(1)
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        private static void addCorsHeaders(HttpExchange exchange) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        }

        private static void writeJson(HttpExchange exchange, int statusCode, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }

        private static String escapeJson(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
