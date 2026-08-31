package com.usic.uniFex.controller.venta;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.repository.FuncionesInscripcion;
import com.usic.uniFex.security.JwtUser;

import lombok.RequiredArgsConstructor;

/**
 * "Mis ventas" del vendedor (Fase F): las inscripciones que registró el usuario del token.
 *
 * No reimplementa nada: envuelve la stored function {@code fn_get_inscripciones(uid[, edicion])},
 * que ya agrupa por inscripcion y categoria y solo cuenta casetas confirmadas (estado 'O').
 * El parametro opcional {@code ?edicion=} filtra por edicion (V6): sin el, se usa la edicion
 * ACTIVA; con el, se ve el historico (p. ej. FEXPO 2025). El aislamiento es por diseño: el id
 * de usuario sale del JWT, nunca de un parametro, asi que un vendedor no puede pedir las ventas
 * de otro. Cualquier usuario autenticado ve las suyas (no necesita rol especial).
 */
@RestController
@RequestMapping("/api/app/mis-ventas")
@RequiredArgsConstructor
public class MisVentasApiController {

    private final FuncionesInscripcion funciones;

    /** Listado de ventas del usuario + un resumen (cuántas inscripciones y el total vendido). */
    @GetMapping
    public ResponseEntity<Map<String, Object>> mias(
            @RequestParam(value = "edicion", required = false) Long edicion) {
        Long uid = usuarioActual();
        if (uid == null) return noAutenticado();

        List<Map<String, Object>> items = funciones.fn_get_inscripciones(uid, edicion);
        if (items == null) items = List.of();

        BigDecimal total = items.stream()
                .map(f -> asBigDecimal(f.get("total_costo")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long inscripciones = items.stream()
                .map(f -> f.get("id_inscripcion"))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        // OJO: aqui NO se puede usar Map.of(). Rechaza valores null con NullPointerException,
        // y `edicion` es null en el caso normal (sin ?edicion=, que significa "la edicion
        // activa"). Esa NPE se convertia en un 500 que la cadena web transformaba en un 302
        // al login: el navegador seguia la redireccion a otro origen y el vendedor solo veia
        // un error de CORS y "NetworkError". Un LinkedHashMap si admite el null.
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cantidad", inscripciones);
        cuerpo.put("total", total);
        cuerpo.put("edicion", edicion);
        cuerpo.put("items", items);
        return ResponseEntity.ok(cuerpo);
    }

    /** Puestos de una inscripción propia. Rechaza (404) si la inscripción no es del usuario. */
    @GetMapping("/{idInscripcion}/puestos")
    public ResponseEntity<?> puestos(@PathVariable Long idInscripcion,
            @RequestParam(value = "edicion", required = false) Long edicion) {
        Long uid = usuarioActual();
        if (uid == null) return noAutenticado();

        boolean esMia = funciones.fn_get_inscripciones(uid, edicion).stream()
                .anyMatch(f -> idInscripcion.equals(asLong(f.get("id_inscripcion"))));
        if (!esMia) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Inscripción no encontrada"));
        }
        return ResponseEntity.ok(funciones.obtener_puestos_por_inscripcion(idInscripcion));
    }

    // ===== helpers =====

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }

    private ResponseEntity<Map<String, Object>> noAutenticado() {
        return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
    }

    private static BigDecimal asBigDecimal(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static Long asLong(Object o) {
        return (o instanceof Number n) ? n.longValue() : null;
    }
}
