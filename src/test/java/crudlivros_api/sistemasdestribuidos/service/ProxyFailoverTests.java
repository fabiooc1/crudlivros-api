package crudlivros_api.sistemasdestribuidos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import crudlivros_api.sistemasdestribuidos.client.BackendHttpClient;
import crudlivros_api.sistemasdestribuidos.client.BackendHttpResponse;
import crudlivros_api.sistemasdestribuidos.config.OrchestratorProperties;
import crudlivros_api.sistemasdestribuidos.exception.BackendCommunicationException;
import crudlivros_api.sistemasdestribuidos.routing.BackendRegistry;
import crudlivros_api.sistemasdestribuidos.routing.BackendStatus;
import tools.jackson.databind.json.JsonMapper;

class ProxyFailoverTests {

    private final StubClient client = new StubClient();
    private BackendRegistry registry;
    private HealthCheckService healthChecks;
    private ProxyService proxy;

    @BeforeEach
    void setUp() {
        var properties = new OrchestratorProperties(
                List.of(URI.create("http://first:8000"), URI.create("http://second:8000"),
                        URI.create("http://third:8000")),
                "/health", Duration.ofSeconds(3), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(3), 3);
        registry = new BackendRegistry(properties);
        registry.nodes().forEach(node -> registry.registerHealth(node, BackendStatus.HEALTHY, node.id()));
        registry.currentLeader();
        healthChecks = new HealthCheckService(registry, client, properties, JsonMapper.builder().build());
        proxy = new ProxyService(registry, client, properties, healthChecks);
    }

    @AfterEach
    void close() {
        healthChecks.shutdownExecutor();
    }

    @Test
    void firstCommunicationFailureChecksBackupAndRetriesWithoutWaitingForSchedule() {
        client.offline.add("first");
        // Mesmo um backup marcado indisponível pode ter se recuperado entre checagens.
        registry.markUnavailable(registry.nodes().get(1), "falha anterior");
        var response = proxy.forward(new MockHttpServletRequest("GET", "/livros"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(registry.currentLeader()).contains(registry.nodes().get(1));
        assertThat(client.calls).containsExactly("first /livros", "second /health", "second /livros");
    }

    @Test
    void skipsBackupWhoseCachedHealthIsStale() {
        client.offline.addAll(Set.of("first", "second"));

        proxy.forward(new MockHttpServletRequest("GET", "/livros"), null);

        assertThat(registry.currentLeader()).contains(registry.nodes().get(2));
        assertThat(client.calls).containsExactly("first /livros", "second /health", "third /health", "third /livros");
    }

    @Test
    void triesNextBackendIfReplacementFailsAfterSuccessfulHealthCheck() {
        client.offline.add("first");
        client.requestFailures.add("second");

        proxy.forward(new MockHttpServletRequest("GET", "/livros"), null);

        assertThat(client.calls).containsExactly("first /livros", "second /health", "second /livros",
                "third /health", "third /livros");
        assertThat(registry.currentLeader()).contains(registry.nodes().get(2));
    }

    @Test
    void electsReplacementForWritesWithoutReplayingOperation() {
        client.offline.add("first");

        assertThatThrownBy(() -> proxy.forward(new MockHttpServletRequest("POST", "/livros"), new byte[] {1}))
                .isInstanceOf(BackendCommunicationException.class);

        assertThat(registry.currentLeader()).contains(registry.nodes().get(1));
        assertThat(client.calls).containsExactly("first /livros", "second /health");
    }

    @Test
    void stopsWhenAllBackendsAreUnavailable() {
        client.offline.addAll(Set.of("first", "second", "third"));

        assertThatThrownBy(() -> proxy.forward(new MockHttpServletRequest("GET", "/livros"), null))
                .isInstanceOf(BackendCommunicationException.class);

        assertThat(registry.currentLeader()).isEmpty();
        assertThat(client.calls).containsExactly("first /livros", "second /health", "third /health");
    }

    @Test
    void checksAvailabilityOnRequestWhenNoLeaderExists() {
        registry.nodes().forEach(node -> registry.markUnavailable(node, "falha anterior"));

        proxy.forward(new MockHttpServletRequest("GET", "/livros"), null);

        assertThat(registry.currentLeader()).contains(registry.nodes().getFirst());
        assertThat(client.calls).containsExactly("first /health", "first /livros");
    }

    private static class StubClient extends BackendHttpClient {
        private final Set<String> offline = new HashSet<>();
        private final Set<String> requestFailures = new HashSet<>();
        private final List<String> calls = new ArrayList<>();

        StubClient() {
            super(HttpClient.newHttpClient());
        }

        @Override
        public BackendHttpResponse exchange(URI uri, String method, Map<String, List<String>> headers,
                byte[] body, Duration timeout) throws IOException {
            calls.add(uri.getHost() + " " + uri.getPath());
            boolean health = uri.getPath().equals("/health");
            if (offline.contains(uri.getHost()) || (!health && requestFailures.contains(uri.getHost()))) {
                throw new IOException("backend offline");
            }
            String payload = health ? "{\"status\":\"ok\",\"servico\":\"test\"}" : "[]";
            return new BackendHttpResponse(200, HttpHeaders.of(Map.of(), (name, value) -> true),
                    payload.getBytes(StandardCharsets.UTF_8));
        }
    }
}
