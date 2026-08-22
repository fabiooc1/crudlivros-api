package crudlivros_api.sistemasdestribuidos.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class BackendHttpClient {

    private static final Set<String> REQUEST_HEADERS_TO_IGNORE = Set.of(
            "connection", "content-length", "expect", "host", "upgrade", "transfer-encoding");

    private final HttpClient httpClient;

    public BackendHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public BackendHttpResponse exchange(
            URI uri,
            String method,
            Map<String, List<String>> headers,
            byte[] body,
            Duration timeout) throws IOException, InterruptedException {

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout);
        headers.forEach((name, values) -> {
            if (!REQUEST_HEADERS_TO_IGNORE.contains(name.toLowerCase())) {
                values.forEach(value -> builder.header(name, value));
            }
        });

        HttpRequest.BodyPublisher publisher = body == null || body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);

        HttpRequest request = builder.method(method, publisher).build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return new BackendHttpResponse(response.statusCode(), response.headers(), response.body());
    }
}
