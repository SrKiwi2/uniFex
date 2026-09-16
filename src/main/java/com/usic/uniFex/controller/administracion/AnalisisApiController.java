package com.usic.uniFex.controller.administracion;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.AnalisisDTO;
import com.usic.uniFex.model.service.AnalisisVentasService;
import com.usic.uniFex.model.service.TablaExportService;
import com.usic.uniFex.model.service.TablaExportService.Columna;
import com.usic.uniFex.model.service.TablaExportService.Tabla;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Los analisis de direccion: cuanto queda por vender, quien vende, que se cobro y a que ritmo.
 *
 * Cada uno se sirve de tres formas —JSON para la pantalla, PDF para imprimir, Excel para
 * seguir trabajando el dato— y las tres salen del MISMO calculo. Es la razon de que la
 * exportacion viva aqui y no en cada servicio: si el PDF recalculara por su cuenta, tarde o
 * temprano el papel diria una cifra y la pantalla otra, y nadie sabria cual creer.
 *
 * Lo ve {@code VE_REPORTES}, que incluye a direccion (ASESORIA) ademas de administracion. Un
 * vendedor no entra: aqui esta el total de la feria y el ranking de sus compañeros.
 */
@RestController
@RequestMapping("/api/app/analisis")
@RequiredArgsConstructor
@PreAuthorize(Roles.VE_REPORTES)
public class AnalisisApiController {

    private final AnalisisVentasService analisis;
    private final TablaExportService exportador;

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ------------------------------------------------------------------ JSON para la pantalla

    /**
     * Todo de una vez.
     *
     * El tablero pinta las cuatro cosas juntas, asi que en cuatro peticiones separadas la
     * pantalla se llenaria a trompicones y —peor— podrian venir de momentos distintos: el
     * total de arriba de hace un segundo y el desglose de hace tres. En una sola peticion,
     * todo lo que se ve es de la misma foto.
     */
    @GetMapping
    public Map<String, Object> todo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ocupacion", analisis.ocupacion());
        m.put("vendedores", analisis.vendedores());
        m.put("cobros", analisis.cobros());
        m.put("avance", analisis.avance());
        return m;
    }

    @GetMapping("/ocupacion")
    public List<AnalisisDTO.Ocupacion> ocupacion() {
        return analisis.ocupacion();
    }

    @GetMapping("/vendedores")
    public List<AnalisisDTO.Vendedor> vendedores() {
        return analisis.vendedores();
    }

    @GetMapping("/cobros")
    public AnalisisDTO.Cobros cobros() {
        return analisis.cobros();
    }

    @GetMapping("/avance")
    public List<AnalisisDTO.Dia> avance() {
        return analisis.avance();
    }

    // ------------------------------------------------------------------ descargas

    /**
     * El mismo reporte en PDF o en Excel, elegido por la ruta.
     *
     * Un solo endpoint para los dos formatos y los cuatro reportes: la alternativa eran ocho
     * metodos que solo se diferencian en dos lineas. Un nombre desconocido responde **400**,
     * no un archivo vacio con nombre bonito.
     */
    @GetMapping("/{nombre}/{formato}")
    public ResponseEntity<byte[]> descargar(@PathVariable String nombre, @PathVariable String formato) {
        Tabla tabla = switch (nombre) {
            case "ocupacion" -> tablaOcupacion();
            case "vendedores" -> tablaVendedores();
            case "cobros" -> tablaCobros();
            case "avance" -> tablaAvance();
            default -> null;
        };
        if (tabla == null) {
            return ResponseEntity.badRequest()
                    .body(("No existe el reporte '" + nombre + "'").getBytes());
        }

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        boolean excel = "excel".equalsIgnoreCase(formato) || "xlsx".equalsIgnoreCase(formato);
        if (!pdf && !excel) {
            return ResponseEntity.badRequest().body("Formato no soportado: usa pdf o excel".getBytes());
        }

        byte[] datos = pdf ? exportador.pdf(tabla) : exportador.excel(tabla);
        String archivo = nombre + (pdf ? ".pdf" : ".xlsx");
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                // `attachment` con nombre: sin esto el navegador abre el XLSX como basura en
                // pantalla o guarda el PDF con el nombre del endpoint.
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .body(datos);
    }

    // ------------------------------------------------------------------ armado de cada tabla

    private Tabla tablaOcupacion() {
        List<List<String>> filas = new ArrayList<>();
        int vendidas = 0, tramite = 0, libres = 0, bloqueadas = 0, total = 0;
        BigDecimal bsVendido = BigDecimal.ZERO, bsPorVender = BigDecimal.ZERO;
        for (AnalisisDTO.Ocupacion o : analisis.ocupacion()) {
            filas.add(List.of(o.categoria() == null ? "(sin categoría)" : o.categoria(),
                    String.valueOf(o.vendidas()), String.valueOf(o.enTramite()),
                    String.valueOf(o.libres()), String.valueOf(o.bloqueadas()),
                    String.valueOf(o.total()), bs(o.porcentajeVendido()) + " %",
                    bs(o.bsVendido()), bs(o.bsPorVender())));
            vendidas += o.vendidas();
            tramite += o.enTramite();
            libres += o.libres();
            bloqueadas += o.bloqueadas();
            total += o.total();
            bsVendido = bsVendido.add(o.bsVendido());
            bsPorVender = bsPorVender.add(o.bsPorVender());
        }
        // El TOTAL va como una fila mas y no en un pie aparte: en Excel un pie flotante rompe
        // el ordenar y el filtrar, que es justo para lo que se baja la hoja.
        int vendible = total - bloqueadas;
        String pct = vendible <= 0 ? "0,0" : bs(BigDecimal.valueOf(vendidas * 100.0 / vendible));
        if (!filas.isEmpty()) {
            filas.add(List.of("TOTAL", String.valueOf(vendidas), String.valueOf(tramite),
                    String.valueOf(libres), String.valueOf(bloqueadas), String.valueOf(total),
                    pct + " %", bs(bsVendido), bs(bsPorVender)));
        }
        return new Tabla("Ocupacion del plano",
                List.of("Edicion activa. No incluye casetas anuladas ni ventas canceladas.",
                        "Bs vendido: precio congelado el dia de la venta. "
                        + "Bs por vender: precio vigente de lo libre y en tramite.",
                        "El % se mide sobre lo VENDIBLE (total menos bloqueadas)."),
                List.of(Columna.texto("Categoria"), Columna.numero("Vendidas"),
                        Columna.numero("En tramite"), Columna.numero("Libres"),
                        Columna.numero("Bloqueadas"), Columna.numero("Total"),
                        Columna.numero("% vendido"), Columna.numero("Bs vendido"),
                        Columna.numero("Bs por vender")),
                filas);
    }

    private Tabla tablaVendedores() {
        List<List<String>> filas = new ArrayList<>();
        for (AnalisisDTO.Vendedor v : analisis.vendedores()) {
            filas.add(List.of(v.vendedor() == null ? "(sin nombre)" : v.vendedor(),
                    v.area() == null ? "—" : v.area(),
                    v.carrera() == null ? "—" : v.carrera(),
                    String.valueOf(v.ventas()), String.valueOf(v.casetas()),
                    bs(v.totalBs()), bs(v.ticketMedio())));
        }
        return new Tabla("Ranking de vendedores",
                List.of("Edicion activa. Solo ventas vivas.",
                        "Ventas = inscripciones; Casetas = casetas de esas ventas. "
                        + "Una venta de tres casetas es UNA venta y TRES casetas.",
                        "Media = Bs / ventas."),
                List.of(Columna.texto("Vendedor"), Columna.texto("Area"), Columna.texto("Carrera"),
                        Columna.numero("Ventas"), Columna.numero("Casetas"),
                        Columna.numero("Total Bs"), Columna.numero("Media Bs")),
                filas);
    }

    private Tabla tablaCobros() {
        AnalisisDTO.Cobros c = analisis.cobros();
        List<List<String>> filas = new ArrayList<>();
        for (AnalisisDTO.Cobro corte : c.cortes()) {
            filas.add(List.of(corte.concepto(), "", "", String.valueOf(corte.ventas()),
                    String.valueOf(corte.casetas()), bs(corte.totalBs()), ""));
        }
        // El detalle va en la MISMA tabla, detras de los cortes y con una fila que lo separa:
        // dos hojas obligarian a cuadrarlas a mano, y el total de arriba es justo la suma de
        // lo de abajo.
        if (!c.pendientes().isEmpty()) {
            filas.add(List.of("", "", "", "", "", "", ""));
            filas.add(List.of("PENDIENTES DE COMPROBANTE", "", "", "", "", "", ""));
            for (AnalisisDTO.Pendiente p : c.pendientes()) {
                filas.add(List.of(p.entidad() == null ? "(sin entidad)" : p.entidad(),
                        p.vendedor() == null ? "—" : p.vendedor(),
                        p.contado() ? "Contado" : "Deposito",
                        "", "", bs(p.totalBs()),
                        p.fecha() == null ? "—" : p.fecha().format(DIA) + " · " + p.dias() + " d"));
            }
        }
        return new Tabla("Cobros: pagado y pendiente",
                List.of("Edicion activa. Solo ventas vivas.",
                        "El comprobante manda sobre la forma de pago: marcar \"contado\" dice COMO "
                        + "se pago, no que el recibo exista.",
                        "Cobrado: " + bs(c.totalCobrado()) + " Bs · Pendiente: "
                        + bs(c.totalPendiente()) + " Bs"),
                List.of(Columna.texto("Concepto / Entidad"), Columna.texto("Vendedor"),
                        Columna.texto("Forma"), Columna.numero("Ventas"), Columna.numero("Casetas"),
                        Columna.numero("Bs"), Columna.texto("Fecha / dias")),
                filas);
    }

    private Tabla tablaAvance() {
        List<List<String>> filas = new ArrayList<>();
        for (AnalisisDTO.Dia d : analisis.avance()) {
            filas.add(List.of(d.fecha() == null ? "—" : d.fecha().format(DIA),
                    String.valueOf(d.ventas()), String.valueOf(d.casetas()), bs(d.totalBs()),
                    String.valueOf(d.acumuladoCasetas()), bs(d.acumuladoBs())));
        }
        return new Tabla("Avance en el tiempo",
                List.of("Edicion activa. Solo ventas vivas.",
                        "Solo salen los dias CON ventas: los vacios no se rellenan con ceros."),
                List.of(Columna.texto("Fecha"), Columna.numero("Ventas"), Columna.numero("Casetas"),
                        Columna.numero("Bs del dia"), Columna.numero("Casetas acum."),
                        Columna.numero("Bs acumulado")),
                filas);
    }

    /** Formato es-BO: el punto agrupa y la coma separa decimales. */
    private static String bs(BigDecimal v) {
        return String.format(java.util.Locale.of("es", "BO"), "%,.2f",
                v == null ? BigDecimal.ZERO : v);
    }
}
