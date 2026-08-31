package com.usic.uniFex.controller.persona;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

import com.usic.uniFex.model.dto.PersonaDTO;
import com.usic.uniFex.model.service.GestionPersonaService;
import com.usic.uniFex.model.service.GestionPersonaService.Datos;
import com.usic.uniFex.model.service.GestionPersonaService.Resultado;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Gestion de personas del sistema para la SPA (Fase H1). Solo administracion. Las personas que se
 * crean aqui quedan ACTIVO y por tanto disponibles en el selector del modulo Usuarios.
 */
@RestController
@RequestMapping("/api/app/personas")
@RequiredArgsConstructor
@PreAuthorize(Roles.GESTIONA_USUARIOS)
public class PersonaApiController {

    private final GestionPersonaService gestion;

    @GetMapping
    public List<PersonaDTO> listar() {
        Set<Long> conUsuario = gestion.idsConUsuario();
        return gestion.listar().stream()
                .map(p -> PersonaDTO.de(p, conUsuario.contains(p.getId())))
                .toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Datos req) {
        return responder(gestion.crear(req, actorId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody Datos req) {
        return responder(gestion.editar(id, req, actorId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        return responder(gestion.eliminar(id, actorId()));
    }

    private ResponseEntity<Map<String, Object>> responder(Resultado r) {
        boolean tiene = r.persona() != null;
        Map<String, Object> cuerpo = tiene
                ? Map.of("ok", r.ok(), "mensaje", r.mensaje(), "persona", PersonaDTO.de(r.persona(), false))
                : Map.of("ok", r.ok(), "mensaje", r.mensaje());
        return ResponseEntity.status(r.ok() ? 200 : 400).body(cuerpo);
    }

    private Long actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }
}
