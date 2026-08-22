package crudlivros_api.sistemasdestribuidos.routing;

import java.net.URI;
import java.time.Instant;

public final class BackendNode {

    private final String id;
    private final int priority;
    private final URI baseUri;
    private String serviceName;
    private BackendStatus status = BackendStatus.UNKNOWN;
    private int consecutiveFailures;
    private Instant lastHealthCheck;

    public BackendNode(String id, int priority, URI baseUri) {
        this.id = id;
        this.priority = priority;
        this.baseUri = normalizeBaseUri(baseUri);
        this.serviceName = id;
    }

    public synchronized void registerHealth(BackendStatus newStatus, String reportedServiceName, Instant checkedAt) {
        status = newStatus;
        consecutiveFailures = 0;
        lastHealthCheck = checkedAt;
        if (reportedServiceName != null && !reportedServiceName.isBlank()) {
            serviceName = reportedServiceName;
        }
    }

    public synchronized boolean registerFailure(int threshold, Instant checkedAt) {
        consecutiveFailures++;
        lastHealthCheck = checkedAt;
        if (consecutiveFailures >= threshold) {
            status = BackendStatus.UNAVAILABLE;
        }
        return status == BackendStatus.UNAVAILABLE;
    }

    public synchronized void registerRequestSuccess() {
        consecutiveFailures = 0;
    }

    public synchronized void markUnavailable(Instant checkedAt) {
        status = BackendStatus.UNAVAILABLE;
        consecutiveFailures = 0;
        lastHealthCheck = checkedAt;
    }

    public synchronized BackendSnapshot snapshot(boolean leader) {
        return new BackendSnapshot(id, serviceName, baseUri, status, consecutiveFailures, lastHealthCheck, priority, leader);
    }

    public synchronized boolean isEligible() {
        return status.isEligible();
    }

    public String id() {
        return id;
    }

    public int priority() {
        return priority;
    }

    public URI baseUri() {
        return baseUri;
    }

    private static URI normalizeBaseUri(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Endereço de backend inválido: " + uri);
        }
        String value = uri.toString();
        return URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
    }
}
