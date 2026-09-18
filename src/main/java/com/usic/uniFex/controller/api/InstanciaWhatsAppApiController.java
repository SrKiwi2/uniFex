package com.usic.uniFex.controller.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.usic.uniFex.model.dto.InstanciaWhatsAppDTO;
import com.usic.uniFex.model.service.InstanciaWhatsAppService;
import com.usic.uniFex.security.Roles;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/whatsapp/instancias")
@PreAuthorize(Roles.ADMINISTRA)
@RequiredArgsConstructor
public class InstanciaWhatsAppApiController {
    private final InstanciaWhatsAppService servicio;

    public record Datos(String nombre, String urlApi, String instancia, String claveApi) {
        @Override public String toString() { return "Datos de instancia WhatsApp"; }
    }
    public record Estado(Boolean activa) {}

    @GetMapping
    public List<InstanciaWhatsAppDTO> listar() { return servicio.listar(); }

    @PostMapping
    public ResponseEntity<InstanciaWhatsAppDTO> crear(@RequestBody Datos datos) {
        return ResponseEntity.status(201).body(servicio.guardar(null, datos.nombre(), datos.urlApi(),
                datos.instancia(), datos.claveApi()));
    }

    @PutMapping("/{id}")
    public InstanciaWhatsAppDTO editar(@PathVariable Long id, @RequestBody Datos datos) {
        return servicio.guardar(id, datos.nombre(), datos.urlApi(), datos.instancia(), datos.claveApi());
    }

    @PatchMapping("/{id}/estado")
    public List<InstanciaWhatsAppDTO> estado(@PathVariable Long id, @RequestBody Estado datos) {
        if (datos.activa() == null) throw new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Indica si la instancia debe estar activa.");
        return servicio.cambiarEstado(id, datos.activa());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("ok", false, "mensaje", error.getReason()));
    }
}
