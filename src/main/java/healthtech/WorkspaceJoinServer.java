package healthtech;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class WorkspaceJoinServer {
    public static void main(String[] args) throws IOException {
        String key = System.getenv("INFRAI_API_KEY");
        WorkspaceJoin workflow = new WorkspaceJoin(new InfraiDirectory(key));
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/join", exchange -> handle(exchange, workflow));
        server.start();
        System.out.println("Workspace join listening on http://localhost:8080/join");
    }

    private static void handle(HttpExchange exchange, WorkspaceJoin workflow) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "POST required"));
            return;
        }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            send(exchange, 200, workflow.join(JsonCodec.object(JsonCodec.read(body))));
        } catch (InfraiDirectory.ApiError e) {
            int status = e.status >= 400 && e.status < 500 ? e.status : 502;
            send(exchange, status, Map.of("error", e.code, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            send(exchange, 502, Map.of("error", "Upstream operation failed"));
        }
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> result) throws IOException {
        byte[] data = JsonCodec.write(result).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (var stream = exchange.getResponseBody()) { stream.write(data); }
    }
}
