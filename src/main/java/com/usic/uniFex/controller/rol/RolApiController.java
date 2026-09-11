package com.usic.uniFex.controller.rol;

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

import com.usic.uniFex.model.dto.RolDTO;
import com.usic.uniFex.model.service.GestionRolService;
import com.usic.uniFex.model.service.GestionRolService.Datos;
import com.usic.uniFex.model.service.GestionRolService.Resultado;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Gestion de roles para la SPA. Solo administracion: un rol decide lo que puede hacer cada
 * usuario, asi que crear uno es tan sensible como crear un login.
 *
 * Los cinco roles del sistema (ver {@code security.RolesSistema}) se listan igual que los demas
 * pero llegan marcados con {@code sistema: true}: no se renombran ni se eliminan. La comprobacion
 * real esta en {@link GestionRolService}; la marca solo permite a la interfaz apagar los botones.
 */
@RestController
@RequestMapping("/api/app/roles")
@RequiredArgsConstructor
@PreAuthorize(Roles.GESTIONA_USUARIOS)
public class RolApiController {

    private final GestionRolService gestion;

    @GetMapping
    public List<RolDTO> listar() {
        return gestion.listar();
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

    /** 200 si la operacion valio, 400 si la rechazo una validacion; el motivo va en el mensaje. */
    private ResponseEntity<Map<String, Object>> responder(Resultado r) {
        Map<String, Object> cuerpo = (r.rol() != null)
                ? Map.of("ok", r.ok(), "mensaje", r.mensaje(), "rol", RolDTO.de(r.rol(), 0L))
                : Map.of("ok", r.ok(), "mensaje", r.mensaje());
        return ResponseEntity.status(r.ok() ? 200 : 400).body(cuerpo);
    }

    private Long actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }
}
