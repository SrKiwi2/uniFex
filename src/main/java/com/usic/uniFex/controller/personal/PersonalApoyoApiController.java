package com.usic.uniFex.controller.personal;

import java.util.HashMap;
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

import com.usic.uniFex.model.dto.PersonalApoyoDTO;
import com.usic.uniFex.model.entity.Dependencia;
import com.usic.uniFex.model.service.GestionPersonalApoyoService;
import com.usic.uniFex.model.service.GestionPersonalApoyoService.Datos;
import com.usic.uniFex.model.service.GestionPersonalApoyoService.DependenciaDatos;
import com.usic.uniFex.model.service.GestionPersonalApoyoService.DependenciaResultado;
import com.usic.uniFex.model.service.GestionPersonalApoyoService.Resultado;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * API para gestión de personal de apoyo de la feria (fotógrafos, azafatas, logística, etc.).
 * Módulo aislado: no toca la tabla persona ni usuario existentes.
 */
@RestController
@RequestMapping("/api/app/personal-apoyo")
@RequiredArgsConstructor
@PreAuthorize(Roles.GESTIONA_USUARIOS)
public class PersonalApoyoApiController {

    private final GestionPersonalApoyoService gestion;

    // ===== DEPENDENCIAS =====

    @GetMapping("/dependencias")
    public List<Map<String, Object>> listarDependencias() {
        return gestion.listarDependencias().stream()
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", d.getId());
                    m.put("nombre", d.getNombre());
                    m.put("descripcion", d.getDescripcion());
                    return m;
                })
                .toList();
    }

    @PostMapping("/dependencias")
    public ResponseEntity<Map<String, Object>> crearDependencia(@RequestBody DependenciaDatos req) {
        return responderDependencia(gestion.crearDependencia(req, actorId()));
    }

    @PatchMapping("/dependencias/{id}")
    public ResponseEntity<Map<String, Object>> editarDependencia(@PathVariable Long id, @RequestBody DependenciaDatos req) {
        return responderDependencia(gestion.editarDependencia(id, req, actorId()));
    }

    @DeleteMapping("/dependencias/{id}")
    public ResponseEntity<Map<String, Object>> eliminarDependencia(@PathVariable Long id) {
        return responderDependencia(gestion.eliminarDependencia(id, actorId()));
    }

    // ===== PERSONAL =====

    @GetMapping
    public List<PersonalApoyoDTO> listar(@RequestParam(required = false) Long dependencia) {
        if (dependencia != null) {
            return gestion.listarPorDependencia(dependencia).stream()
                    .map(PersonalApoyoDTO::de)
                    .toList();
        }
        return gestion.listarPersonal().stream()
                .map(PersonalApoyoDTO::de)
                .toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Datos req) {
        return responder(gestion.crearPersonal(req, actorId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody Datos req) {
        return responder(gestion.editarPersonal(id, req, actorId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        return responder(gestion.eliminarPersonal(id, actorId()));
    }

    // ===== helpers =====

    private ResponseEntity<Map<String, Object>> responder(Resultado r) {
        boolean tiene = r.personal() != null;
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        if (tiene) cuerpo.put("personal", PersonalApoyoDTO.de(r.personal()));
        return ResponseEntity.status(r.ok() ? 200 : 400).body(cuerpo);
    }

    private ResponseEntity<Map<String, Object>> responderDependencia(DependenciaResultado r) {
        boolean tiene = r.dependencia() != null;
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        if (tiene) {
            Map<String, Object> dep = new HashMap<>();
            dep.put("id", r.dependencia().getId());
            dep.put("nombre", r.dependencia().getNombre());
            dep.put("descripcion", r.dependencia().getDescripcion());
            cuerpo.put("dependencia", dep);
        }
        return ResponseEntity.status(r.ok() ? 200 : 400).body(cuerpo);
    }

    private Long actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }
}