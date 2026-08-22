package crudlivros_api.sistemasdestribuidos.dto;

import java.time.Instant;
import java.util.List;

import crudlivros_api.sistemasdestribuidos.routing.BackendSnapshot;

public record OrchestratorStatusResponse(
        String status,
        String leader,
        Instant lastLeadershipChange,
        List<BackendSnapshot> backends) {
}
