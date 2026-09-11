package com.usic.uniFex.controller.inscripcion;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Notificacion;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.AuditoriaService;
import com.usic.uniFex.model.service.NotificacionService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bandeja de notificaciones persistentes + tiempo real, bajo /api/app.
 *
 * Endpoints:
 * - GET /notificaciones              → bandeja completa (no leídas primero)
 * - GET /notificaciones/no-leidas    → solo no leídas (para badge)
 * - GET /notificaciones/count        → contador numérico
 * - POST /notificaciones/{id}/leer   → marca como leída
 * - POST /notificaciones/leer-todas  → marca todas como leídas
 * - GET /notificaciones/{id}/hilo    → hilo completo de observación
 * - POST /notificaciones/{id}/responder → vendedor responde a admin
 * - POST /notificaciones/{id}/resolver  → admin cierra hilo (RESUELTA)
 *
 * Admin → vendedor (OBSERVACION_ADMIN): se crea desde la vista de administración
 * (pendiente de endpoint dedicado o desde Inscripciones).
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@Slf4j
public class NotificacionApiController {

    private final NotificacionService notificacionService;
    private final IUsuarioDao usuarioDao;

    /** Bandeja completa del usuario autenticado (no leídas primero). */
    @GetMapping("/notificaciones")
    public ResponseEntity<?> bandeja() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(notificacionService.getBandeja(u));
    }

    /** Solo no leídas (para mostrar en dropdown/bell). */
    @GetMapping("/notificaciones/no-leidas")
    public ResponseEntity<?> noLeidas() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(notificacionService.getNoLeidas(u));
    }

    /** Contador numérico (para badge en la barra). */
    @GetMapping("/notificaciones/count")
    public ResponseEntity<?> countNoLeidas() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(Map.of("count", notificacionService.countNoLeidas(u)));
    }

    /** Marca una notificación como leída. */
    @PostMapping("/notificaciones/{id}/leer")
    public ResponseEntity<?> marcarLeida(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        notificacionService.marcarLeida(id, u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Marca todas las notificaciones del usuario como leídas. */
    @PostMapping("/notificaciones/leer-todas")
    public ResponseEntity<?> marcarTodasLeidas() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        notificacionService.marcarTodasLeidas(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Hilo completo de una observación (padre + respuestas). */
    @GetMapping("/notificaciones/{id}/hilo")
    public ResponseEntity<?> hilo(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        // El service ya filtra por usuario destino en la consulta del hilo
        return ResponseEntity.ok(notificacionService.getHilo(id));
    }

    /** Vendedor responde a una observación de admin. */
    @PostMapping("/notificaciones/{id}/responder")
    public ResponseEntity<?> responder(@PathVariable Long id,
                                       @RequestBody Map<String, String> body) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario vendedor = usuarioDao.findById(usuarioId).orElse(null);
        if (vendedor == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        String respuesta = body != null ? body.get("respuesta") : null;
        if (respuesta == null || respuesta.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "La respuesta es obligatoria"));
        }
        notificacionService.responderObservacion(id, vendedor, respuesta.trim());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Admin cierra el hilo (marca RESUELTA). */
    @PostMapping("/notificaciones/{id}/resolver")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> resolver(@PathVariable Long id,
                                      @RequestBody Map<String, String> body,
                                      @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long adminId = usuarioActual();
        if (adminId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario admin = usuarioDao.findById(adminId).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        String respuesta = body != null ? body.get("respuesta") : null;
        if (respuesta == null || respuesta.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "La respuesta es obligatoria"));
        }
        notificacionService.resolverHilo(id, admin, respuesta.trim());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Admin envía observación a un vendedor sobre una venta/puesto. */
    @PostMapping("/notificaciones/observacion")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> crearObservacion(@RequestBody Map<String, Object> body,
                                              @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long adminId = usuarioActual();
        if (adminId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario admin = usuarioDao.findById(adminId).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }

        Long vendedorId = ((Number) body.get("vendedorId")).longValue();
        String asunto = (String) body.get("asunto");
        String cuerpo = (String) body.get("cuerpo");
        Long inscripcionId = body.get("inscripcionId") != null ? ((Number) body.get("inscripcionId")).longValue() : null;
        Long puestoId = body.get("puestoId") != null ? ((Number) body.get("puestoId")).longValue() : null;

        if (vendedorId == null || asunto == null || asunto.trim().isEmpty() || cuerpo == null || cuerpo.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "vendedorId, asunto y cuerpo son obligatorios"));
        }

        notificacionService.notificar(vendedorId, NotificacionService.TIPO_OBSERVACION_ADMIN,
                asunto.trim(), cuerpo.trim(), inscripcionId, puestoId);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Id del usuario del token, o null si no hay sesión válida. */
    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}