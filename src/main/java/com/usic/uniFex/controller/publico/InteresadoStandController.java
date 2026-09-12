package com.usic.uniFex.controller.publico;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.service.InteresadoStandEventPublisher;
import com.usic.uniFex.model.service.InteresadoStandService;
import com.usic.uniFex.model.service.InteresadoStandService.Resultado;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Registro público de personas interesadas en exponer ("Quiero exponer" en la SPA).
 * Sin autenticación, igual que {@link FeriaPublicaController}: solo da de alta. El listado
 * y la edición viven en el panel "Interesados en exponer"
 * ({@code interesado/InteresadoStandApiController}, bajo /api/app, solo administración).
 */
@RestController
@RequestMapping("/api/publico/interesados-stand")
@RequiredArgsConstructor
public class InteresadoStandController {

    private final InteresadoStandService interesadoStandService;
    private final InteresadoStandEventPublisher publisher;

    /** 201 con el id; 400 con {@code mensaje} diciendo qué campo no cumple (lo muestra el modal). */
    @PostMapping
    public ResponseEntity<Map<String, Object>> registrar(@Valid @RequestBody InteresadoStandRequest datos) {
        Resultado r = interesadoStandService.registrar(datos);
        if (!r.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
        }
        // Ya confirmado (la transaccion del servicio termino): aparece en vivo en el panel.
        publisher.publicar(r.interesado());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "id", r.interesado().getId()));
    }
}
