package com.usic.uniFex.controller.inscripcion;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dto.InscripcionDetalleDTO;
import com.usic.uniFex.model.dto.InscripcionListadoDTO;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.service.AuditoriaService;
import com.usic.uniFex.model.service.CancelarInscripcionService;
import com.usic.uniFex.model.service.ReciboPdfService;
import com.usic.uniFex.model.service.RegistroVentaService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Inscripciones (ventas) desde la SPA.
 *
 * **La autorizacion va por metodo, no por clase.** Antes toda la clase estaba anotada con
 * {@code ADMINISTRA}, lo cual servia mientras solo habia un listado de administracion; pero
 * registrar una venta es precisamente lo que hace el vendedor, asi que una anotacion de clase
 * habria dejado fuera a los 35 usuarios que mas la necesitan.
 */
@RestController
@RequestMapping("/api/app/inscripciones")
@RequiredArgsConstructor
@Slf4j
public class InscripcionApiController {

    private final IInscripcionService inscripcionService;
    private final RegistroVentaService registroVenta;
    private final ReciboPdfService reciboPdfService;
    private final CancelarInscripcionService cancelarInscripcion;

    /**
     * Listado de inscripciones. Solo administracion.
     *
     * {@code ?canceladas=true} devuelve el historico (baja logica) con el motivo y
     * quien/cuando las cancelo; por defecto, las activas.
     */
    @GetMapping
    @PreAuthorize(Roles.ADMINISTRA)
    public List<InscripcionListadoDTO> listar(
            @RequestParam(defaultValue = "false") boolean canceladas) {
        return inscripcionService.listarParaTabla(canceladas);
    }

    /**
     * Detalle completo de una inscripcion: datos de la venta, responsables, casetas con
     * su costo, datos de cancelacion y la traza de auditoria del ciclo de vida
     * (quien/cuando/desde donde). Solo administracion.
     */
    @GetMapping("/{id}")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<InscripcionDetalleDTO> detalle(@PathVariable Long id) {
        InscripcionDetalleDTO d = inscripcionService.detalleParaTabla(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    /**
     * Cancela una venta: libera sus casetas (vuelven a 'L' en el mapa, via WebSocket),
     * la pasa al historico de canceladas y deja huella de auditoria.
     *
     * **Quien puede cancelar (V11):** administracion directamente, con motivo obligatorio;
     * el vendedor que registro la venta SOLO despues de que administracion aprobo su
     * solicitud de cancelacion (en ese caso el motivo es el de la solicitud).
     *
     * Codigos: 200 cancelada · 400 motivo vacio o ya estaba cancelada, o vendedor sin
     * solicitud aprobada · 403 ajena · 404 inexistente · 409 no se pudo.
     */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelar(
            @PathVariable Long id,
            @RequestBody(required = false) CancelarInscripcionService.Peticion peticion,
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
        CancelarInscripcionService.Resultado r =
                cancelarInscripcion.cancelar(id, motivo, usuarioId, origenDe(origen), esAdministracion());
        if (!r.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("puestosLiberados", r.puestosLiberados());
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Registra una venta completa: entidad, responsables, inscripcion y casetas, todo en una
     * transaccion. Cualquier usuario autenticado puede vender.
     *
     * Codigos: 200 si se registro · 400 si faltan datos · **409 si alguna caseta dejo de estar
     * disponible** (la SPA distingue por codigo, no por el texto del mensaje).
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> registrar(
            @RequestBody RegistroVentaService.NuevaVenta req,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        try {
            RegistroVentaService.Resultado r = registroVenta.registrar(req, usuarioId, origenDe(origen));
            if (!r.ok()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("ok", false, "mensaje", r.mensaje()));
            }
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("mensaje", r.mensaje());
            cuerpo.put("inscripcionId", r.inscripcionId());
            cuerpo.put("puestos", r.puestosOcupados());
            cuerpo.put("total", r.total());
            return ResponseEntity.ok(cuerpo);
        } catch (RegistroVentaService.CasetaNoDisponibleException e) {
            // 409 y no 400: no es que el vendedor mandara mal los datos, es que otro gano la
            // carrera. La venta entera quedo revertida.
            return ResponseEntity.status(409).body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    /**
     * Ventas propias que siguen sin comprobante de pago, con los dias que llevan asi.
     *
     * Cualquier vendedor ve LAS SUYAS: el id sale del token, nunca de un parametro, igual
     * que en "mis ventas".
     */
    @GetMapping("/mis-pendientes")
    public ResponseEntity<?> misPendientes() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        return ResponseEntity.ok(registroVenta.pendientesDe(usuarioId));
    }

    /**
     * Adjunta (o reemplaza) el comprobante de pago de una venta propia.
     *
     * Es multipart y no JSON porque viaja un archivo — en el movil, una foto recien tomada.
     * Va aparte del registro a proposito: cerrar la venta asegura la caseta, y el comprobante
     * puede subirse despues, cuando haya señal.
     */
    @PostMapping(value = "/{id}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> adjuntarComprobante(
            @PathVariable Long id,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(value = "entidadBancaria", required = false) String entidadBancaria,
            @RequestParam(value = "numComprobante", required = false) Long numComprobante,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        RegistroVentaService.Resultado r =
                registroVenta.adjuntarComprobante(id, archivo, entidadBancaria, numComprobante, usuarioId, origenDe(origen));
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    /**
     * Recibo de la venta en PDF.
     *
     * Lo puede descargar **quien la registro**, o administracion. Esa comprobacion no existia
     * en el equivalente Thymeleaf ({@code /ver/inscripcion/{id}/recibo.pdf}), donde cualquier
     * usuario con sesion podia bajarse el recibo de cualquier otro vendedor — con los datos
     * del cliente dentro. Aqui no se repite.
     */
    @GetMapping("/{id}/recibo")
    public ResponseEntity<byte[]> recibo(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return ResponseEntity.status(401).build();

        Inscripcion i = inscripcionService.findById(id);
        if (i == null) return ResponseEntity.notFound().build();
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion()) {
            return ResponseEntity.status(403).build();
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            reciboPdfService.generarRecibo(id, salida);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    // `inline` y no `attachment`: en el movil interesa verlo antes de guardarlo,
                    // y para descargarlo basta el boton del propio visor.
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=recibo-" + id + ".pdf")
                    .body(salida.toByteArray());
        } catch (Exception e) {
            log.error("No se pudo generar el recibo de la inscripcion {}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /** Id del usuario del token, o null si no hay sesion valida. */
    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }

    /**
     * De donde llega la peticion, para la auditoria: WEB por defecto, o APK si el
     * cliente (el futuro empaquetado Capacitor) envia el header {@code X-Origen}.
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
