package crudlivros_api.sistemasdestribuidos.service;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import crudlivros_api.sistemasdestribuidos.client.BackendHttpClient;
import crudlivros_api.sistemasdestribuidos.client.BackendHttpResponse;
import crudlivros_api.sistemasdestribuidos.config.OrchestratorProperties;
import crudlivros_api.sistemasdestribuidos.exception.BackendCommunicationException;
import crudlivros_api.sistemasdestribuidos.exception.NoBackendAvailableException;
import crudlivros_api.sistemasdestribuidos.routing.BackendNode;
import crudlivros_api.sistemasdestribuidos.routing.BackendRegistry;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class ProxyService {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> RESPONSE_HEADERS_TO_IGNORE = Set.of(
            "connection", "content-length", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade",
            "access-control-allow-origin", "access-control-allow-methods",
            "access-control-allow-headers", "access-control-allow-credentials",
            "access-control-expose-headers", "access-control-max-age");

    private final BackendRegistry registry;
    private final BackendHttpClient httpClient;
    private final OrchestratorProperties properties;
    private final HealthCheckService healthCheckService;

    public ProxyService(
            BackendRegistry registry,
            BackendHttpClient httpClient,
            OrchestratorProperties properties,
            HealthCheckService healthCheckService) {
        this.registry = registry;
        this.httpClient = httpClient;
        this.properties = properties;
        this.healthCheckService = healthCheckService;
    }

    public ResponseEntity<byte[]> forward(HttpServletRequest request, byte[] body) {
        Set<BackendNode> attempted = new HashSet<>();
        BackendNode leader = registry.currentLeader()
                .or(() -> healthCheckService.findAvailableReplacement(attempted))
                .orElseThrow(NoBackendAvailableException::new);
        while (true) {
            attempted.add(leader);
            try {
                return execute(leader, request, body);
            } catch (IOException exception) {
                registry.markUnavailable(leader, "falha de comunicação: " + exception.getClass().getSimpleName());
                var replacement = healthCheckService.findAvailableReplacement(attempted);
                if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT)) && replacement.isPresent()) {
                    leader = replacement.get();
                    continue;
                }
                throw new BackendCommunicationException("Não foi possível obter resposta do backend líder", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                registry.registerFailure(leader, "requisição interrompida");
                throw new BackendCommunicationException("A comunicação com o backend foi interrompida", exception);
            }
        }
    }

    private ResponseEntity<byte[]> execute(BackendNode node, HttpServletRequest request, byte[] body)
            throws IOException, InterruptedException {
        URI target = buildTargetUri(node, request);
        BackendHttpResponse response = httpClient.exchange(
                target,
                request.getMethod(),
                requestHeaders(request),
                body,
                properties.proxyReadTimeout());

        if (response.statusCode() >= 500) {
            registry.registerFailure(node, "HTTP " + response.statusCode());
        } else {
            registry.registerRequestSuccess(node);
        }
        return toClientResponse(response, node);
    }

    private URI buildTargetUri(BackendNode node, HttpServletRequest request) {
        StringBuilder target = new StringBuilder(node.baseUri().toString()).append(request.getRequestURI());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            target.append('?').append(request.getQueryString());
        }
        return URI.create(target.toString());
    }

    private Map<String, List<String>> requestHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }

    private ResponseEntity<byte[]> toClientResponse(BackendHttpResponse response, BackendNode node) {
        HttpHeaders headers = new HttpHeaders();
        response.headers().map().forEach((name, values) -> {
            if (!RESPONSE_HEADERS_TO_IGNORE.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(value -> headers.add(name, value));
            }
        });
        headers.set("X-Active-Backend", node.snapshot(true).serviceName());
        return ResponseEntity.status(response.statusCode()).headers(headers).body(response.body());
    }
}
