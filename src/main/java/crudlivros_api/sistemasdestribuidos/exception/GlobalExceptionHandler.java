package crudlivros_api.sistemasdestribuidos.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import crudlivros_api.sistemasdestribuidos.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoBackendAvailableException.class)
    ResponseEntity<ErrorResponse> handleNoBackend(NoBackendAvailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(BackendCommunicationException.class)
    ResponseEntity<ErrorResponse> handleCommunication(BackendCommunicationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(exception.getMessage()));
    }
}
