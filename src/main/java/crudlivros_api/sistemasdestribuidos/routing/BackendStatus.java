package crudlivros_api.sistemasdestribuidos.routing;

public enum BackendStatus {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
    UNKNOWN;

    public boolean isEligible() {
        return this == HEALTHY || this == DEGRADED;
    }
}
