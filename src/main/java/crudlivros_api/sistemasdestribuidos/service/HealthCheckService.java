package crudlivros_api.sistemasdestribuidos.service;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import crudlivros_api.sistemasdestribuidos.client.BackendHttpClient;
import crudlivros_api.sistemasdestribuidos.client.BackendHttpResponse;
import crudlivros_api.sistemasdestribuidos.config.OrchestratorProperties;
import crudlivros_api.sistemasdestribuidos.dto.BackendHealthResponse;
import crudlivros_api.sistemasdestribuidos.routing.BackendNode;
import crudlivros_api.sistemasdestribuidos.routing.BackendRegistry;
import crudlivros_api.sistemasdestribuidos.routing.BackendStatus;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    private final BackendRegistry registry;
    private final BackendHttpClient httpClient;
    private final OrchestratorProperties properties;
    private final JsonMapper jsonMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public HealthCheckService(
            BackendRegistry registry,
            BackendHttpClient httpClient,
            OrchestratorProperties properties,
            JsonMapper jsonMapper) {
        this.registry = registry;
        this.httpClient = httpClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Scheduled(fixedDelayString = "${orchestrator.health-check-interval}")
    public synchronized void checkAllBackends() {
        CompletableFuture<?>[] checks = registry.nodes().stream()
                .map(node -> CompletableFuture.runAsync(() -> check(node, false), executor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(checks).join();
        registry.currentLeader();
    }

    public synchronized Optional<BackendNode> findAvailableReplacement(Set<BackendNode> excluded) {
        // A lista preserva a prioridade configurada em BACKENDS.
        for (BackendNode node : registry.nodes()) {
            if (!excluded.contains(node) && check(node, true)) {
                registry.promote(node, "disponibilidade confirmada após falha de comunicação");
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private boolean check(BackendNode node, boolean immediate) {
        try {
            URI uri = URI.create(node.baseUri() + properties.healthPath());
            BackendHttpResponse response = httpClient.exchange(
                    uri, "GET", Map.of("Accept", java.util.List.of("application/json")), null,
                    properties.healthCheckTimeout());

            BackendHealthResponse health = jsonMapper.readValue(response.body(), BackendHealthResponse.class);
            BackendStatus status = mapStatus(response.statusCode(), health.status());
            if (status == BackendStatus.UNAVAILABLE) {
                registry.markUnavailable(node, "backend declarou status indisponível");
            } else {
                registry.registerHealth(node, status, health.servico());
            }
            return status.isEligible();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            registry.registerFailure(node, "health-check interrompido");
        } catch (Exception exception) {
            if (immediate) {
                registry.markUnavailable(node, "checagem imediata falhou: " + exception.getClass().getSimpleName());
            } else {
                registry.registerFailure(node, exception.getClass().getSimpleName());
            }
            log.debug("Health-check de {} falhou: {}", node.id(), exception.getMessage());
        }
        return false;
    }

    private BackendStatus mapStatus(int statusCode, String reportedStatus) {
        if (statusCode == 503 || "indisponivel".equalsIgnoreCase(reportedStatus)) {
            return BackendStatus.UNAVAILABLE;
        }
        if (statusCode >= 200 && statusCode < 300 && "ok".equalsIgnoreCase(reportedStatus)) {
            return BackendStatus.HEALTHY;
        }
        if (statusCode >= 200 && statusCode < 300 && "degradado".equalsIgnoreCase(reportedStatus)) {
            return BackendStatus.DEGRADED;
        }
        return BackendStatus.UNAVAILABLE;
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.close();
    }
}
