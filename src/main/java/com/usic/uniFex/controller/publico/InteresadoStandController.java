package com.usic.uniFex.controller.publico;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.entity.InteresadoStand;
import com.usic.uniFex.model.service.InteresadoStandService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Registro público de personas interesadas en exponer ("Quiero exponer" en la SPA).
 * Sin autenticación, igual que {@link FeriaPublicaController}: solo escribe, no hay
 * listado ni gestión todavía — eso queda para cuando exista un panel de administración
 * para estos leads.
 */
@RestController
@RequestMapping("/api/publico/interesados-stand")
@RequiredArgsConstructor
public class InteresadoStandController {

    private final InteresadoStandService interesadoStandService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registrar(@Valid @RequestBody InteresadoStandRequest datos) {
        InteresadoStand guardado = interesadoStandService.registrar(datos);
        return Map.of("ok", true, "id", guardado.getId());
    }
}
