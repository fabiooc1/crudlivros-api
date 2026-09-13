package crudlivros_api.sistemasdestribuidos.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BackendHealthResponse(
        String status,
        String servico,
        @JsonProperty("banco_principal") DatabaseHealthResponse bancoPrincipal,
        @JsonProperty("banco_replica") DatabaseHealthResponse bancoReplica,
        Instant timestamp) {
}
