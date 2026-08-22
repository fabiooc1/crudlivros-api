package crudlivros_api.sistemasdestribuidos.exception;

public class NoBackendAvailableException extends RuntimeException {

    public NoBackendAvailableException() {
        super("Nenhum backend está disponível no momento");
    }
}
