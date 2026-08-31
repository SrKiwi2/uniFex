package com.usic.uniFex.controller.usuario;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IRolService;
import com.usic.uniFex.model.dto.UsuarioDTO;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.service.GestionUsuarioService;
import com.usic.uniFex.model.service.GestionUsuarioService.Resultado;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Gestion de usuarios para la SPA (Fase E). Solo administracion: crear un login es la operacion
 * mas sensible del sistema. La contraseña nunca sale en ninguna respuesta (ver UsuarioDTO).
 */
@RestController
@RequestMapping("/api/app/usuarios")
@RequiredArgsConstructor
@PreAuthorize(Roles.GESTIONA_USUARIOS)
public class UsuarioApiController {

    private static final int MAX_PERSONAS = 30;

    private final GestionUsuarioService gestion;
    private final IRolService rolService;
    private final IPersonaService personaService;

    @GetMapping
    public List<UsuarioDTO> listar() {
        return gestion.listar().stream().map(UsuarioDTO::de).toList();
    }

    /** Roles disponibles para el selector del formulario. */
    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return rolService.findAll().stream()
                .map(r -> Map.<String, Object>of("id", r.getId(), "nombre", r.getNombre()))
                .toList();
    }

    /**
     * Personas para asignar a un usuario. Con {@code q} filtra por nombre o CI; sin él devuelve
     * las primeras. Se limita a {@value #MAX_PERSONAS} porque hay cientos y un selector no las
     * necesita todas de golpe.
     */
    @GetMapping("/personas")
    public List<Map<String, Object>> personas(@RequestParam(value = "q", required = false) String q) {
        String filtro = q == null ? "" : q.trim().toLowerCase();
        return personaService.listarPersonas().stream()
                .filter(p -> filtro.isEmpty() || nombreCi(p).contains(filtro))
                .limit(MAX_PERSONAS)
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "nombre", nombreCompleto(p),
                        "ci", p.getCi() == null ? "" : p.getCi()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody CrearUsuario req) {
        return responder(gestion.crear(req.username(), req.password(), req.personaId(), req.rolId(), actorId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody EditarUsuario req) {
        return responder(gestion.editar(id, req.username(), req.personaId(), req.rolId(), actorId()));
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Map<String, Object>> password(@PathVariable Long id, @RequestBody PasswordReq req) {
        return responder(gestion.cambiarPassword(id, req.password()));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Map<String, Object>> estado(@PathVariable Long id, @RequestBody EstadoReq req) {
        return responder(gestion.cambiarEstado(id, Boolean.TRUE.equals(req.activo()), actorId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        return responder(gestion.eliminar(id, actorId()));
    }

    // ===== records de request =====
    public record CrearUsuario(String username, String password, Long personaId, Long rolId) {}
    public record EditarUsuario(String username, Long personaId, Long rolId) {}
    public record PasswordReq(String password) {}
    public record EstadoReq(Boolean activo) {}

    // ===== helpers =====

    /** Traduce el Resultado del servicio a HTTP: 200 si ok, 400 (validacion) si no. */
    private ResponseEntity<Map<String, Object>> responder(Resultado r) {
        Map<String, Object> cuerpo = (r.usuario() != null)
                ? Map.of("ok", r.ok(), "mensaje", r.mensaje(), "usuario", UsuarioDTO.de(r.usuario()))
                : Map.of("ok", r.ok(), "mensaje", r.mensaje());
        return ResponseEntity.status(r.ok() ? 200 : 400).body(cuerpo);
    }

    private Long actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }

    private static String nombreCompleto(Persona p) {
        return java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b).orElse("(sin nombre)");
    }

    private static String nombreCi(Persona p) {
        return (nombreCompleto(p) + " " + (p.getCi() == null ? "" : p.getCi())).toLowerCase();
    }
}
