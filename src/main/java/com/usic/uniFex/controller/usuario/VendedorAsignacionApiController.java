package com.usic.uniFex.controller.usuario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.VendedorAsignacionService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Que casetas tiene habilitadas cada vendedor (rol ADMINISTRATIVO).
 *
 * **Una sola via de habilitacion: las casetas que se le seleccionan.** Hubo otra por categoria
 * entera y un "cupo" aparte; las dos podian contradecir a la seleccion (con la categoria asignada,
 * seleccionarle 10 casetas no servia de nada porque le seguian saliendo todas). Se retiraron en
 * V23: si se le habilitan 10 casetas, ve 10 y vende 10.
 *
 * El mapa filtrado del vendedor lo sirve PuestoApiController, que usa este mismo servicio.
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class VendedorAsignacionApiController {

    private final VendedorAsignacionService service;

    /** Los vendedores del sistema, para la tabla de administracion. */
    @GetMapping("/vendedores")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<Usuario> listarVendedores() {
        return service.listarVendedores();
    }

    /** Las casetas habilitadas a un vendedor, con sus datos. */
    @GetMapping("/vendedores/{id}/puestos")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<Puesto> getPuestos(@PathVariable Long id) {
        return service.getPuestosAsignados(id);
    }

    /**
     * Catalogo del modal: todas las casetas vivas con su categoria y su duenio actual.
     * Una sola peticion para toda la pantalla; agrupar y filtrar se hace en el cliente.
     */
    @GetMapping("/vendedores/puestos-asignables")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<VendedorAsignacionService.CasetaAsignable> catalogoAsignable() {
        return service.catalogoAsignable();
    }

    /**
     * Guarda de una vez TODAS las casetas del vendedor (la seleccion final, no un cambio suelto).
     *
     * Existe porque habilitar veinte casetas de una en una son veinte peticiones, y en la practica
     * se habilitan por rangos. Aqui son dos consultas, pase lo que pase.
     */
    @PutMapping("/vendedores/{id}/puestos")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> reemplazarPuestos(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long adminId = usuarioActual();
        if (adminId == null) return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        Object crudo = body == null ? null : body.get("puestoIds");
        List<Long> ids = (crudo instanceof List<?> l)
                ? l.stream().filter(o -> o instanceof Number).map(o -> ((Number) o).longValue()).toList()
                : List.of();
        try {
            VendedorAsignacionService.ResultadoAsignacion r = service.reemplazarPuestos(id, ids, adminId);
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("asignadas", r.asignadas());
            cuerpo.put("quitadas", r.quitadas());
            cuerpo.put("noDisponibles", r.noDisponibles());
            cuerpo.put("mensaje", r.noDisponibles().isEmpty()
                    ? "Casetas habilitadas"
                    : r.noDisponibles().size() + " caseta(s) ya son de otro vendedor y no se tomaron");
            return ResponseEntity.ok(cuerpo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    /** Retira una caseta suelta. El modal usa el guardado en lote; esto es para casos puntuales. */
    @DeleteMapping("/vendedores/{id}/puestos/{puestoId}")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> quitarPuesto(@PathVariable Long id, @PathVariable Long puestoId) {
        service.quitarPuesto(id, puestoId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Las casetas del vendedor autenticado. Las pinta su propia pantalla. */
    @GetMapping("/mis-puestos")
    public ResponseEntity<?> misPuestos() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        return ResponseEntity.ok(service.getPuestosVisiblesParaVendedor(usuarioId));
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }
}
