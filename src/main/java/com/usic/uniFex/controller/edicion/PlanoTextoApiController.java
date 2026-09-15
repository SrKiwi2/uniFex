package com.usic.uniFex.controller.edicion;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.PlanoTextoDTO;
import com.usic.uniFex.model.service.PlanoTextoService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Rotulos libres del plano: "ENTRADA", "TARIMA", "ZONA A".
 *
 * LEER lo puede cualquier usuario autenticado —el mapa de un vendedor los tiene que pintar—;
 * ESCRIBIR es del mismo grupo que edita el plano, porque un rotulo mal puesto desorienta a
 * todos los vendedores a la vez.
 */
@RestController
@RequestMapping("/api/app/plano-textos")
@RequiredArgsConstructor
public class PlanoTextoApiController {

    private final PlanoTextoService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PlanoTextoDTO> listar() {
        return service.listar();
    }

    @PostMapping
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<?> crear(@RequestBody PlanoTextoService.CambioTexto req) {
        try {
            return ResponseEntity.ok(service.crear(req, usuarioActual()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<?> actualizar(@PathVariable Long id,
                                        @RequestBody PlanoTextoService.CambioTexto req) {
        PlanoTextoDTO d = service.actualizar(id, req, usuarioActual());
        return d == null
                ? ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "El rotulo no existe"))
                : ResponseEntity.ok(d);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        return service.eliminar(id, usuarioActual())
                ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "El rotulo no existe"));
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}
