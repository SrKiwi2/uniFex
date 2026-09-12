package com.usic.uniFex.controller.usuario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.PermisoPantallaService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.PantallasSistema;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Que pantallas ve cada rol.
 *
 * {@code /mias} lo llama CUALQUIER usuario autenticado al entrar: es lo que arma su menu. El
 * resto es de administracion.
 */
@RestController
@RequestMapping("/api/app/permisos")
@RequiredArgsConstructor
public class PermisoApiController {

    private final PermisoPantallaService permisos;

    /** El catalogo de pantallas, con su nombre y su grupo, para pintar la matriz. */
    @GetMapping("/catalogo")
    @PreAuthorize(Roles.GESTIONA_USUARIOS)
    public List<Map<String, Object>> catalogo() {
        return PantallasSistema.todas().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clave", p.getClave());
            m.put("titulo", p.getTitulo());
            m.put("grupo", p.getGrupo());
            return m;
        }).toList();
    }

    @GetMapping
    @PreAuthorize(Roles.GESTIONA_USUARIOS)
    public List<Map<String, Object>> matriz() {
        return permisos.matriz();
    }

    public record Seleccion(String rol, List<String> pantallas) {
    }

    @PutMapping("/{rolId}")
    @PreAuthorize(Roles.GESTIONA_USUARIOS)
    public ResponseEntity<Map<String, Object>> guardar(@PathVariable Long rolId,
                                                       @RequestBody Seleccion req) {
        try {
            List<String> guardadas = permisos.guardar(
                    rolId, req == null ? null : req.rol(),
                    req == null ? null : req.pantallas(), usuarioActual());
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "pantallas", guardadas,
                    "mensaje", "Permisos guardados"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    /**
     * Las pantallas del que esta dentro. Sin rol de por medio: es lo que la SPA necesita para
     * armar el menu y decidir a que rutas deja entrar.
     */
    @GetMapping("/mias")
    public Map<String, Object> mias() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof JwtUser u)) {
            return Map.of("pantallas", List.of());
        }
        return Map.of(
                "rol", u.rol(),
                "loVeTodo", PermisoPantallaService.loVeTodo(u.rol()),
                "pantallas", permisos.pantallasDe(u.id(), u.rol()));
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}
