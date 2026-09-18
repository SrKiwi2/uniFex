package com.usic.uniFex.controller.administracion;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.dto.ResumenCategoriaView;
import com.usic.uniFex.model.dto.ResumenEntidadView;
import com.usic.uniFex.model.dto.ResumenGeneralView;
import com.usic.uniFex.model.dto.ReporteVentaDTO;
import com.usic.uniFex.model.dto.ResponsableReporteView;
import com.usic.uniFex.model.service.ReporteResponsablesExcelService;
import com.usic.uniFex.model.service.ReporteVentasExcelService;
import com.usic.uniFex.model.service.ReporteVentasPdfService;
import com.usic.uniFex.model.service.ReporteVentasService;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Reportes globales de la feria para la SPA (Fase G). Aqui se ve el total —todas las
 * inscripciones, todos los vendedores—, a diferencia de "mis ventas", que es lo propio.
 *
 * <b>Se protege con {@link Roles#VE_REPORTES} y no con GESTIONA_USUARIOS.</b> Estaba con lo
 * segundo, que nunca cuadro: esto es de solo lectura y no crea ni toca una sola cuenta. El
 * efecto practico era que a direccion (ASESORIA) le salia "Reportes" en el menu y respondia
 * 403 al entrar — el fallo mudo que este proyecto ya sufrio con VERIFICADOR.
 *
 * Se apoya en las proyecciones {@code resumenPor*} de IInscripcionService, que —al reves que
 * fn_get_inscripciones— NO filtran por casetas confirmadas ('O'), asi que cuentan todo lo
 * inscrito y devuelven datos aunque en la copia local los puestos esten reseteados.
 */
@RestController
@RequestMapping("/api/app/reportes")
@RequiredArgsConstructor
@PreAuthorize(Roles.VE_REPORTES)
public class ReportesApiController {

    private final IInscripcionService inscripcionService;
    private final IResponsableService responsableService;
    private final ReporteVentasService reporteVentas;
    private final ReporteVentasPdfService reporteVentasPdf;
    private final ReporteVentasExcelService reporteVentasExcel;
    private final ReporteResponsablesExcelService reporteResponsablesExcel;

    /** KPIs generales: nº de inscripciones, nº de puestos y total en Bs. */
    @GetMapping("/resumen")
    public ResumenGeneralView resumen() {
        return inscripcionService.resumenGeneral();
    }

    /** Desglose por vendedor y categoria (inscripciones, puestos, total). */
    @GetMapping("/por-categoria")
    public List<ResumenCategoriaView> porCategoria() {
        return inscripcionService.resumenPorCategoria();
    }

    /** Desglose por vendedor y entidad (inscripciones, puestos, total). */
    @GetMapping("/por-entidad")
    public List<ResumenEntidadView> porEntidad() {
        return inscripcionService.resumenPorEntidad();
    }

    @GetMapping("/ventas/filtros")
    public java.util.Map<String, Object> filtrosVentas() {
        return reporteVentas.filtros();
    }

    @GetMapping("/ventas")
    public List<ReporteVentaDTO> ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String responsable,
            @RequestParam(required = false) List<Long> categorias,
            @RequestParam(required = false) List<Long> promotores) {
        return reporteVentas.buscar(new ReporteVentasService.Filtros(desde, hasta, responsable,
                categorias == null ? List.of() : categorias,
                promotores == null ? List.of() : promotores));
    }

    @GetMapping("/ventas/pdf")
    public ResponseEntity<byte[]> ventasPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String responsable,
            @RequestParam(required = false) List<Long> categorias,
            @RequestParam(required = false) List<Long> promotores) {
        ReporteVentasService.Filtros filtros = new ReporteVentasService.Filtros(desde, hasta, responsable,
                categorias == null ? List.of() : categorias,
                promotores == null ? List.of() : promotores);
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            reporteVentasPdf.generar(reporteVentas.buscar(filtros), filtros, salida);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=reporte-ventas.pdf")
                    .body(salida.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("No se pudo generar el reporte de ventas: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/ventas/excel")
    public ResponseEntity<byte[]> ventasExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String responsable,
            @RequestParam(required = false) List<Long> categorias,
            @RequestParam(required = false) List<Long> promotores) {
        ReporteVentasService.Filtros filtros = new ReporteVentasService.Filtros(desde, hasta, responsable,
                categorias == null ? List.of() : categorias,
                promotores == null ? List.of() : promotores);
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            reporteVentasExcel.generar(reporteVentas.buscar(filtros), filtros, salida);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-ventas.xlsx")
                    .body(salida.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("No se pudo generar el Excel de ventas: " + e.getMessage()).getBytes());
        }
    }

    /** Listado de todos los responsables de la edición activa con estado de credencial y foto. */
    @GetMapping("/responsables")
    public List<ResponsableReporteView> responsables() {
        return responsableService.listarParaReporte();
    }

    /** Excel del reporte de responsables. */
    @GetMapping("/responsables/excel")
    public ResponseEntity<byte[]> responsablesExcel() {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            reporteResponsablesExcel.generar(responsableService.listarParaReporte(), salida);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-responsables.xlsx")
                    .body(salida.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("No se pudo generar el Excel de responsables: " + e.getMessage()).getBytes());
        }
    }
}
