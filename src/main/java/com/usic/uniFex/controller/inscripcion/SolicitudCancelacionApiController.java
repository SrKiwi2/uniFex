package com.usic.uniFex.controller.inscripcion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dto.SolicitudCancelacionDTO;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.service.AuditoriaService;
import com.usic.uniFex.model.service.SolicitudCancelacionService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Flujo de cancelacion con aprobacion (V11), bajo /api/app.
 *
 * - El VENDEDOR que registro la venta solicita cancelarla con un motivo
 *   (POST /inscripciones/{id}/solicitar-cancelacion) y queda a la espera; se le
 *   notifica por WebSocket cuando administracion resuelve.
 * - La ADMINISTRACION ve la cola de pendientes, aprueba o rechaza, y su decision
 *   llega al vendedor al instante, sin que ninguno recargue la pagina.
 *
 * Autorizacion: la cola y la resolucion son de administracion (mismo rol que el
 * listado de inscripciones); solicitar es del dueño de la venta (o administracion),
 * misma regla que el recibo.
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@Slf4j
public class SolicitudCancelacionApiController {

    private final SolicitudCancelacionService solicitudService;
    private final IInscripcionService inscripcionService;

    /**
     * El vendedor solicita cancelar una venta propia, con el motivo obligatorio.
     * La solicitud nace PENDIENTE y administracion recibe el aviso por WebSocket.
     */
    @PostMapping("/inscripciones/{id}/solicitar-cancelacion")
    public ResponseEntity<Map<String, Object>> solicitar(
            @PathVariable Long id,
            @RequestBody(required = false) SolicitudCancelacionService.PeticionSolicitud peticion,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Inscripcion i = inscripcionService.findById(id);
        if (i == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "La venta no existe"));
        }
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion()) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "mensaje", "Esta venta no es tuya"));
        }

        String motivo = peticion != null ? peticion.motivo() : null;
        SolicitudCancelacionService.Resultado r =
                solicitudService.solicitar(id, motivo, usuarioId, origenDe(origen));
        if (!r.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("solicitudId", r.solicitudId());
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * El vendedor consulta el estado de su solicitud para una venta concreta (en espera,
     * aprobada o rechazada). Puede verla quien registro la venta, o administracion.
     */
    @GetMapping("/inscripciones/{id}/solicitud-cancelacion")
    public ResponseEntity<?> estadoSolicitud(
            @PathVariable Long id,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Inscripcion i = inscripcionService.findById(id);
        if (i == null) {
            return ResponseEntity.notFound().build();
        }
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion()) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "mensaje", "Esta venta no es tuya"));
        }
        return ResponseEntity.ok(solicitudService.estadoDeVenta(id));
    }

    /**
     * La cola de pendientes de administracion, en orden de llegada. Es lo que se ve al
     * abrir la pestana "Solicitudes": cada pendiente trae la venta y quien la pidio.
     */
    @GetMapping("/solicitudes-cancelacion/pendientes")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<SolicitudCancelacionDTO> pendientes() {
        return solicitudService.pendientes();
    }

    /** El historico de resueltas (aprobadas y rechazadas), las mas recientes primero. */
    @GetMapping("/solicitudes-cancelacion/resueltas")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<SolicitudCancelacionDTO> resueltas() {
        return solicitudService.resueltas();
    }

    /** Mis solicitudes de cancelacion (las que pidio este vendedor), de la mas reciente a la mas vieja. */
    @GetMapping("/mis-solicitudes-cancelacion")
    public ResponseEntity<?> misSolicitudes() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        return ResponseEntity.ok(solicitudService.delVendedor(usuarioId));
    }

    /**
     * Administracion aprueba una solicitud pendiente: el vendedor queda habilitado para
     * cancelar y recibe el aviso por WebSocket al instante. Solo se aprueba lo pendiente:
     * si otro admin ya la resolvio, 400 "ya fue resuelta".
     */
    @PostMapping("/solicitudes-cancelacion/{id}/aprobar")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> aprobar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long adminId = usuarioActual();
        if (adminId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        SolicitudCancelacionService.Resultado r = solicitudService.aprobar(id, adminId, origenDe(origen));
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    /**
     * Administracion rechaza una solicitud pendiente; la respuesta es obligatoria para que
     * el vendedor sepa por que no procedio. El aviso le llega por WebSocket.
     */
    @PostMapping("/solicitudes-cancelacion/{id}/rechazar")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> rechazar(
            @PathVariable Long id,
            @RequestBody(required = false) SolicitudCancelacionService.PeticionResolucion peticion,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long adminId = usuarioActual();
        if (adminId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        String respuesta = peticion != null ? peticion.respuesta() : null;
        SolicitudCancelacionService.Resultado r = solicitudService.rechazar(id, respuesta, adminId, origenDe(origen));
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    /** Id del usuario del token, o null si no hay sesion valida. */
    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }

    /**
     * De donde llega la peticion, para la auditoria: WEB por defecto, o APK si el
     * cliente envia el header {@code X-Origen}.
     */
    private static String origenDe(String origen) {
        return AuditoriaService.ORIGEN_APK.equalsIgnoreCase(origen == null ? "" : origen.trim())
                ? AuditoriaService.ORIGEN_APK
                : AuditoriaService.ORIGEN_WEB;
    }

    /** ¿El usuario del token pertenece a administracion? (ver Roles.AUTORIDADES_ADMINISTRA) */
    private boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.AUTORIDADES_ADMINISTRA::contains);
    }
}
