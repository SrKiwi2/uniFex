package com.usic.uniFex.controller.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.usic.uniFex.model.dto.VerificarRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private ApiService apiService;

    @Value("${api.key}")
    private String apiKey;

    @PostMapping("/verificarInscripcionPuesto")
    public ResponseEntity<Map<String, Object>> verificarInscripcionPuesto(
            @RequestHeader(value = "X-API-KEY", required = true) String headerKey,
            @RequestBody VerificarRequest request) {

        Map<String, Object> response = new HashMap<>();

        // Validar API Key
        if (!apiKey.equals(headerKey)) {
            response.put("error", "API Key inválida");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }

        Integer resultado = apiService.verificarInscripcionPuesto(
                request.getCi(),
                request.getIdEntidad()
        );

        response.put("resultado", resultado);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
