package crudlivros_api.sistemasdestribuidos.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crudlivros_api.sistemasdestribuidos.dto.OrchestratorStatusResponse;
import crudlivros_api.sistemasdestribuidos.routing.BackendRegistry;
import crudlivros_api.sistemasdestribuidos.routing.BackendSnapshot;

@RestController
@RequestMapping("/backends")
public class OrchestratorController {

    private final BackendRegistry registry;

    public OrchestratorController(BackendRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public OrchestratorStatusResponse status() {
        List<BackendSnapshot> snapshots = registry.snapshots();
        String leader = snapshots.stream()
                .filter(BackendSnapshot::leader)
                .map(BackendSnapshot::serviceName)
                .findFirst()
                .orElse(null);
        return new OrchestratorStatusResponse(
                leader == null ? "indisponivel" : "ok",
                leader,
                registry.lastLeadershipChange(),
                snapshots);
    }

    @GetMapping("/health")
    public ResponseEntity<OrchestratorStatusResponse> health() {
        OrchestratorStatusResponse response = status();
        HttpStatus status = response.leader() == null ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
