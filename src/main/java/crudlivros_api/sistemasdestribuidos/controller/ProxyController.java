package crudlivros_api.sistemasdestribuidos.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import crudlivros_api.sistemasdestribuidos.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping({"/", "/{*path}"})
    public ResponseEntity<byte[]> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return proxyService.forward(request, body);
    }
}
