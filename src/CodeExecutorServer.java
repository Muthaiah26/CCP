import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import com.google.gson.Gson; // Import Gson

public class CodeExecutorServer {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/run", new RunHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Code Executor Server running at http://localhost:8080/run");
    }

    static class RunHandler implements HttpHandler {
        private static final Gson gson = new Gson();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("Incoming request: " + exchange.getRequestMethod());
            System.out.flush();

            // CORS headers handling
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin","http://localhost:3000");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            // Handle OPTIONS preflight request
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                // Handle POST request
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                // Read the entire request body
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes());

                System.out.println("Raw JSON body received:");
            System.out.println(body);
            System.out.println("---------------------------");

            // Deserialize JSON
            CodeRequest request = gson.fromJson(body, CodeRequest.class);

             // Log decoded multi-line code
            System.out.println("Decoded code from frontend:");
            System.out.println("---------------------------");
            System.out.println(request.getCode()); // This will show actual multi-line code without backslashes
            System.out.println("---------------------------");

                

               

                 String result = CodeRunner.runCode(request.getLanguage(), request.getCode());

                // Build and send the JSON response
                // Correctly escape the output string to ensure it's valid JSON
                String responseJson = "{ \"output\": \"" + gson.toJson(result).replaceAll("^\"|\"$", "") + "\" }";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] responseBytes = responseJson.getBytes();
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.getResponseBody().close();

            } catch (Exception e) {
                // Handle any exceptions during processing
                String errorResponse = "{ \"error\": \"Server error: " + e.getMessage() + "\" }";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] responseBytes = errorResponse.getBytes();
                exchange.sendResponseHeaders(500, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.getResponseBody().close();
            }
        }
    }
    
    // Helper class to map the JSON structure
    static class CodeRequest {
        private String language;
        private String code;

        public String getLanguage() {
            return language;
        }

        public String getCode() {
            return code;
        }
    }
}