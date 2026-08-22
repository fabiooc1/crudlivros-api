package crudlivros_api.sistemasdestribuidos.client;

import java.net.http.HttpHeaders;

public record BackendHttpResponse(int statusCode, HttpHeaders headers, byte[] body) {
}
