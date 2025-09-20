package com.usic.uniFex.controller.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.IAccesoResponsableService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/acceso")
public class ControlAccesoApi {
    private final IAccesoResponsableService service;

    @GetMapping(value = "/estado", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> estado(@RequestParam("ci") String ci) {
        var out = service.estadoPorCi(ci);
        return ResponseEntity.status((boolean)out.getOrDefault("ok", false) ? 200 : 404).body(out);
    }

    @PostMapping(value = "/entrada", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> entrada(@RequestParam("ci") String ci) {
        var out = service.entrarPorCi(ci);
        return ResponseEntity.status((boolean)out.getOrDefault("ok", false) ? 200 : 400).body(out);
    }

    @PostMapping(value = "/salida", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> salida(@RequestParam("ci") String ci) {
        var out = service.salirPorCi(ci);
        return ResponseEntity.status((boolean)out.getOrDefault("ok", false) ? 200 : 400).body(out);
    }
}
