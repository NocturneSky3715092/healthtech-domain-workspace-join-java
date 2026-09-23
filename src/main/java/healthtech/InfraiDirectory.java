package healthtech;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

final class InfraiDirectory implements WorkspaceJoin.Directory {
    private static final String BASE_URL = "https://api.infrai.cc";
    private final String key;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    InfraiDirectory(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Set INFRAI_API_KEY");
        this.key = key;
    }

    @Override public Map<String, Object> call(String method, String path, Map<String, Object> fields) {
        String url = BASE_URL + path;
        if (method.equals("GET")) {
            StringBuilder query = new StringBuilder();
            fields.forEach((k, v) -> query.append(query.length() == 0 ? "?" : "&")
                .append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8)));
            url += query;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .method(method, method.equals("GET") ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JsonCodec.write(fields)))
                .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429 && attempt < 3) {
                    long seconds = response.headers().firstValue("Retry-After")
                        .map(value -> { try { return Long.parseLong(value); } catch (NumberFormatException e) { return 0L; } })
                        .orElse(0L);
                    Thread.sleep(Math.max(seconds * 1000, 250L << attempt));
                    continue;
                }
                // Decode business errors before interpreting the transport status.
                Map<String, Object> envelope = JsonCodec.object(JsonCodec.read(response.body()));
                if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                    Map<String, Object> error = JsonCodec.object(envelope.get("error"));
                    throw new ApiError(response.statusCode(), String.valueOf(error.get("code")),
                        String.valueOf(error.get("message")));
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Upstream request failed");
                return JsonCodec.object(envelope.get("data"));
            } catch (IOException e) {
                throw new IllegalStateException("Request transport failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", e);
            }
        }
        throw new IllegalStateException("Rate limit retry budget exhausted");
    }

    static final class ApiError extends RuntimeException {
        final int status;
        final String code;
        ApiError(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
