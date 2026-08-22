package crudlivros_api.sistemasdestribuidos.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator")
public record OrchestratorProperties(
        List<URI> backends,
        String healthPath,
        Duration healthCheckInterval,
        Duration healthCheckTimeout,
        Duration proxyConnectTimeout,
        Duration proxyReadTimeout,
        int failureThreshold) {

    public OrchestratorProperties {
        backends = backends == null ? List.of() : List.copyOf(backends);
        if (backends.isEmpty()) {
            throw new IllegalArgumentException("Configure ao menos um endereço em BACKENDS");
        }
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("FAILURE_THRESHOLD deve ser maior que zero");
        }
        healthPath = normalizeHealthPath(healthPath);
    }

    private static String normalizeHealthPath(String path) {
        if (path == null || path.isBlank()) {
            return "/health";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
