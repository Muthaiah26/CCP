import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;

public class CodeExecutorServer {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/run", new RunHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Code Executor Server running at http://localhost:8080/run");
    }

    static class RunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes());

            // --- Simple manual JSON parsing ---
            String language = extractJsonValue(body, "language");
            String code = extractJsonValue(body, "code").replace("\\n", "\n");

            String result = CodeRunner.runCode(language, code);

            // Build JSON response manually
            String responseJson = "{ \"output\": \"" + escapeJson(result) + "\" }";

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] responseBytes = responseJson.getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.getResponseBody().close();
        }

        // Extract value from simple JSON key
        private String extractJsonValue(String json, String key) {
            try {
                String[] parts = json.split("\"" + key + "\"\\s*:\\s*\"");
                if (parts.length < 2) return "";
                return parts[1].split("\"")[0];
            } catch (Exception e) {
                return "";
            }
        }

        // Escape quotes and newlines in JSON string
        private String escapeJson(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
        }
    }
}
