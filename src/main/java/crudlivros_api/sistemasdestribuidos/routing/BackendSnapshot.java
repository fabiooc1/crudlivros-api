package crudlivros_api.sistemasdestribuidos.routing;

import java.net.URI;
import java.time.Instant;

public record BackendSnapshot(
        String id,
        String serviceName,
        URI baseUri,
        BackendStatus status,
        int consecutiveFailures,
        Instant lastHealthCheck,
        int priority,
        boolean leader) {
}
