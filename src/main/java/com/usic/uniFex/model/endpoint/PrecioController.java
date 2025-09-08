package com.usic.uniFex.model.endpoint;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.repository.FuncionesInscripcion;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PrecioController {
    private final FuncionesInscripcion funcionesInscripcion;

    @GetMapping("/costo-puesto")
    public ResponseEntity<Map<String, Object>> costoPuesto(
            @RequestParam Long tipoEntidad,
            @RequestParam Long categoriaId,
            @RequestParam(required = false, defaultValue = "3x3") String tamano,  // ajusta si manejas otros tamaños
            @RequestParam(required = false, defaultValue = "1") Integer cantidad
    ) {
        BigDecimal unitPrice = funcionesInscripcion.obtenerCostoPuesto(tipoEntidad, tamano, categoriaId);
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;

        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(Math.max(1, cantidad)));
        Map<String, Object> body = new HashMap<>();
        body.put("unitPrice", unitPrice);
        body.put("quantity", cantidad);
        body.put("total", total);
        return ResponseEntity.ok(body);
    }
}