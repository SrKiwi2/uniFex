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
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Control de ventas: la rendicion de cuentas de la feria. Lo vendido, lo recaudado con
 * comprobante y lo que falta rendir, por venta, por vendedor y por categoria.
 *
 * <b>Solo lectura, con {@link Roles#VE_REPORTES}.</b> Direccion (ASESORIA) ve las cifras. Rendir
 * una venta es adjuntarle su comprobante, y eso ya existe: {@code POST
 * /api/app/inscripciones/{id}/comprobante} (el vendedor en lo suyo; administracion en todo). La
 * pantalla lo llama desde la ficha para no tener dos formas de marcar una venta como pagada.
 * Un vendedor no entra: aqui estan las ventas de todos sus compañeros.
 *
 * Los filtros viajan igual a todos los GET —pantalla, Excel y PDF— para que lo descargado sea
 * exactamente lo que se estaba mirando. El PDF filtrado por un vendedor es su hoja de rendicion.
 */
@RestController
@RequestMapping("/api/app/control-ventas")
@RequiredArgsConstructor
@PreAuthorize(Roles.VE_REPORTES)
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
        return control.tablero(new Filtros(vendedor, categoria, desde, hasta, estado, q));
    }

    @GetMapping("/filtros")
    public Map<String, Object> filtros() {
        Map<String, Object> r = new LinkedHashMap<>(control.opcionesDeFiltro());
        // La pantalla esconde "Adjuntar comprobante" a quien no puede usarlo. Es comodidad: el
        // servicio del comprobante comprueba igual que la venta sea suya o que sea administracion.
        r.put("puedeAdjuntar", esAdministracion());
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
        return control.ventas(new Filtros(vendedor, categoria, desde, hasta, estado, q));
    }

    @GetMapping("/ventas/{id}")
    public ResponseEntity<?> ficha(@PathVariable Long id) {
        return control.ficha(id)
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
        byte[] datos = exportacion.excel(new Filtros(vendedor, categoria, desde, hasta, estado, q));
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
        byte[] datos = exportacion.pdf(new Filtros(vendedor, categoria, desde, hasta, estado, q));
        String archivo = vendedor == null ? "control-ventas.pdf" : "rendicion-vendedor-" + vendedor + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .body(datos);
    }

    private static boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .anyMatch(g -> Roles.AUTORIDADES_ADMINISTRA.contains(g.getAuthority()));
    }
}
