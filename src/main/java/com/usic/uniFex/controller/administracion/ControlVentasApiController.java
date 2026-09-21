package com.usic.uniFex.controller.administracion;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.ControlVentasDTO;
import com.usic.uniFex.model.service.ControlVentasExportService;
import com.usic.uniFex.model.service.ControlVentasService;
import com.usic.uniFex.model.service.ControlVentasService.Filtros;
import com.usic.uniFex.security.AccesoPantallas;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Control de ventas: la rendicion de cuentas de la feria. Lo vendido, lo recaudado con
 * comprobante y lo que falta rendir, por venta, por vendedor y por categoria.
 *
 * <b>Solo lectura.</b> Los roles de {@link Roles#VE_REPORTES} ven toda la feria. Cualquier otro
 * usuario con la pantalla asignada (por rol o por usuario, V49) ve SOLO SU rendicion: el filtro
 * de vendedor se le fuerza en todo —tablero, lista, ficha, Excel y PDF—, porque aqui estan las
 * ventas y los clientes de todos sus compañeros.
 *
 * Rendir una venta es adjuntarle su comprobante, y eso ya existe: {@code POST
 * /api/app/inscripciones/{id}/comprobante} (el vendedor en lo suyo; administracion en todo). La
 * pantalla lo llama desde la ficha para no tener dos formas de marcar una venta como pagada.
 *
 * Los filtros viajan igual a todos los GET —pantalla, Excel y PDF— para que lo descargado sea
 * exactamente lo que se estaba mirando. El PDF filtrado por un vendedor es su hoja de rendicion.
 */
@RestController
@RequestMapping("/api/app/control-ventas")
@RequiredArgsConstructor
@PreAuthorize(Roles.VE_CONTROL_VENTAS)
public class ControlVentasApiController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ControlVentasService control;
    private final ControlVentasExportService exportacion;

    /** El tablero entero en una peticion, para que todo lo que se ve sea de la misma foto. */
    @GetMapping
    public ControlVentasDTO.Tablero tablero(
            @RequestParam(required = false) Long vendedor,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        return control.tablero(filtrosDe(vendedor, categoria, desde, hasta, estado, q));
    }

    @GetMapping("/filtros")
    public Map<String, Object> filtros() {
        Map<String, Object> r = new LinkedHashMap<>(control.opcionesDeFiltro());
        Long soloDe = soloDe();
        if (soloDe != null) {
            // Solo el mismo en el desplegable: ofrecer a los demas daria listas vacias.
            @SuppressWarnings("unchecked")
            List<ControlVentasDTO.Opcion> todos = (List<ControlVentasDTO.Opcion>) r.get("vendedores");
            r.put("vendedores", todos.stream().filter(o -> soloDe.equals(o.id())).toList());
        }
        r.put("soloPropias", soloDe != null);
        // "Adjuntar comprobante": administracion en todo; los demas solo ven ventas suyas, y el
        // servicio del comprobante deja al dueño adjuntar a lo suyo. Es comodidad: el servidor
        // lo vuelve a comprobar al subirlo.
        r.put("puedeAdjuntar", esAdministracion() || soloDe != null);
        return r;
    }

    @GetMapping("/ventas")
    public List<ControlVentasDTO.Venta> ventas(
            @RequestParam(required = false) Long vendedor,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        return control.ventas(filtrosDe(vendedor, categoria, desde, hasta, estado, q));
    }

    @GetMapping("/ventas/{id}")
    public ResponseEntity<?> ficha(@PathVariable Long id) {
        Long soloDe = soloDe();
        return control.ficha(id)
                // Una venta ajena responde 404, no 403: no se confirma que ese id exista.
                .filter(f -> soloDe == null || soloDe.equals(f.venta().getVendedorId()))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("ok", false, "mensaje", "La venta no existe, está anulada o es de otra edición")));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam(required = false) Long vendedor,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        byte[] datos = exportacion.excel(filtrosDe(vendedor, categoria, desde, hasta, estado, q));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"control-ventas.xlsx\"")
                .body(datos);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam(required = false) Long vendedor,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        Filtros f = filtrosDe(vendedor, categoria, desde, hasta, estado, q);
        byte[] datos = exportacion.pdf(f);
        String archivo = f.vendedorId() == null ? "control-ventas.pdf" : "rendicion-vendedor-" + f.vendedorId() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .body(datos);
    }

    /**
     * Los filtros de la peticion, con el vendedor FORZADO al usuario si no ve toda la feria. Se
     * fuerza aqui, en un solo sitio, y no en cada metodo: un GET que se olvidara de recortar
     * enseñaria las ventas de todos.
     */
    private static Filtros filtrosDe(Long vendedor, Long categoria, LocalDate desde, LocalDate hasta,
                                     String estado, String q) {
        Long soloDe = soloDe();
        return new Filtros(soloDe != null ? soloDe : vendedor, categoria, desde, hasta, estado, q);
    }

    /** null = ve toda la feria; si no, el id del usuario (solo su rendicion). */
    private static Long soloDe() {
        return AccesoPantallas.soloDe(Roles.AUTORIDADES_VEN_REPORTES);
    }

    private static boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .anyMatch(g -> Roles.AUTORIDADES_ADMINISTRA.contains(g.getAuthority()));
    }
}
