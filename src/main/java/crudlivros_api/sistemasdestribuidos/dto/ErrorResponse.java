package crudlivros_api.sistemasdestribuidos.dto;

import java.time.Instant;

public record ErrorResponse(String erro, Instant timestamp) {

    public ErrorResponse(String erro) {
        this(erro, Instant.now());
    }
}
