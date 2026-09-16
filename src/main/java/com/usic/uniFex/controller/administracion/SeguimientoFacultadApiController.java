package com.usic.uniFex.controller.administracion;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.entity.Area;
import com.usic.uniFex.model.service.SeguimientoFacultadService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Seguimiento de la venta por facultad (area academica).
 *
 * <h2>Quien ve que</h2>
 * Quien MONITOREA ve su facultad y solo la suya: el area se resuelve desde su usuario en el
 * servidor y **se ignora** el parametro `areaId` si lo manda. Si el area viajara en la peticion,
 * cambiar un numero en la URL bastaria para mirar otra facultad.
 *
 * Administracion si puede elegir cualquiera —es quien asigna— y es la unica que puede cambiar
 * las asignaciones.
 *
 * <h2>Sin importes</h2>
 * Ninguna respuesta de aqui lleva precios ni totales en bolivianos. Es deliberado y esta
 * sostenido desde la consulta: el DAO no selecciona `costo`.
 */
@RestController
@RequestMapping("/api/app/seguimiento-facultad")
@RequiredArgsConstructor
public class SeguimientoFacultadApiController {

    private final SeguimientoFacultadService service;

    /**
     * El informe de la facultad que toca.
     *
     * @param areaId solo lo respeta administracion; a los demas se les da la suya.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> informe(@RequestParam(required = false) Long areaId) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return ResponseEntity.status(401).body(Map.of("ok", false));

        Long objetivo;
        if (esAdministracion()) {
            // Administracion mira la que pida; si no pide ninguna, la suya, y si tampoco tiene
            // —lo normal, porque asigna en vez de monitorear— la primera del catalogo.
            Area suya = service.areaDe(usuarioId);
            Area porDefecto = suya != null ? suya : service.primeraArea();
            if (areaId == null && porDefecto == null) {
                return ResponseEntity.ok(Map.of(
                        "ok", false, "sinAsignar", true,
                        "mensaje", "Todavía no hay facultades cargadas en el sistema."));
            }
            objetivo = areaId != null ? areaId : porDefecto.getId();
        } else {
            Area suya = service.areaDe(usuarioId);
            if (suya == null) {
                // No es un error: es que todavia no le asignaron facultad. Se dice con palabras
                // en vez de devolver un informe vacio, que parece una averia.
                return ResponseEntity.ok(Map.of(
                        "ok", false,
                        "sinAsignar", true,
                        "mensaje", "Todavía no tienes una facultad asignada. Pídeselo a administración."));
            }
            objetivo = suya.getId();
        }

        SeguimientoFacultadService.Informe informe = service.informe(objetivo);
        return informe == null
                ? ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Esa facultad no existe"))
                : ResponseEntity.ok(informe);
    }

    /** Quien monitorea cada facultad. Para la pantalla de configuracion. */
    @GetMapping("/asignaciones")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<Map<String, Object>> asignaciones() {
        return service.asignaciones();
    }

    /** Asigna la facultad que monitorea un usuario. `areaId` nulo se la quita. */
    @PutMapping("/asignaciones/{usuarioId}")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> asignar(@PathVariable Long usuarioId,
                                     @RequestBody(required = false) Map<String, Object> cuerpo) {
        Object crudo = cuerpo == null ? null : cuerpo.get("areaId");
        Long areaId = (crudo instanceof Number n) ? n.longValue() : null;
        try {
            return service.asignar(usuarioId, areaId, usuarioActual())
                    ? ResponseEntity.ok(Map.of("ok", true))
                    : ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "El usuario no existe"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    private boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(x -> "ROLE_SUPER_USUARIO".equals(x) || "ROLE_ADMINISTRADOR".equals(x));
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}
