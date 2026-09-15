package com.usic.uniFex.controller.administracion;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.AnuncioDTO;
import com.usic.uniFex.model.service.AnuncioService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Anuncios para todos los usuarios.
 *
 * LOS VIGENTES los lee cualquier autenticado —son para el—; publicar y retirar es de
 * administracion, porque un anuncio sale en la pantalla de los 35 vendedores a la vez.
 */
@RestController
@RequestMapping("/api/app/anuncios")
@RequiredArgsConstructor
public class AnuncioApiController {

    private final AnuncioService service;

    /** Los que hay que enseñar ahora. Lo pide la aplicacion al entrar. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AnuncioDTO> vigentes() {
        return service.vigentes();
    }

    /** Todo lo publicado, incluido lo retirado y lo vencido. Para la pantalla que los gestiona. */
    @GetMapping("/historial")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<AnuncioDTO> historial() {
        return service.historial();
    }

    @PostMapping
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> publicar(@RequestBody AnuncioService.NuevoAnuncio req) {
        try {
            return ResponseEntity.ok(service.publicar(req, usuarioActual()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> retirar(@PathVariable Long id) {
        return service.retirar(id, usuarioActual())
                ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "El anuncio no existe"));
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}
