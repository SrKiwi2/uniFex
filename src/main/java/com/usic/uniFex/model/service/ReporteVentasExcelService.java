package com.usic.uniFex.model.service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.dto.ReporteVentaDTO;

@Service
public class ReporteVentasExcelService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void generar(List<ReporteVentaDTO> ventas, ReporteVentasService.Filtros filtros,
                        OutputStream salida) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet hoja = wb.createSheet("Ventas realizadas");
            Estilos estilos = new Estilos(wb);
            int fila = 0;

            Row titulo = hoja.createRow(fila++);
            celda(titulo, 0, "REPORTE DE VENTAS REALIZADAS", estilos.titulo);
            fila = agregarResumen(hoja, fila, ventas == null ? List.of() : ventas, filtros, estilos);
            fila++;
            agregarTabla(hoja, fila, ventas == null ? List.of() : ventas, estilos);

            for (int i = 0; i < 9; i++) hoja.autoSizeColumn(i);
            hoja.createFreezePane(0, fila + 1);
            wb.write(salida);
        }
    }

    private int agregarResumen(Sheet hoja, int fila, List<ReporteVentaDTO> ventas,
                               ReporteVentasService.Filtros filtros, Estilos estilos) {
        Row periodo = hoja.createRow(fila++);
        celda(periodo, 0, "Periodo", estilos.etiqueta);
        celda(periodo, 1, describirPeriodo(filtros), estilos.normal);

        Row totales = hoja.createRow(fila++);
        celda(totales, 0, "Ventas", estilos.etiqueta);
        numero(totales, 1, ventas.size(), estilos.numero);
        celda(totales, 2, "Casetas", estilos.etiqueta);
        numero(totales, 3, ventas.stream().mapToInt(v -> v.cantidadCasetas() == null ? 0 : v.cantidadCasetas()).sum(), estilos.numero);
        celda(totales, 4, "Total Bs", estilos.etiqueta);
        decimal(totales, 5, total(ventas), estilos.moneda);
        return fila;
    }

    private void agregarTabla(Sheet hoja, int fila, List<ReporteVentaDTO> ventas, Estilos estilos) {
        Row cab = hoja.createRow(fila++);
        String[] columnas = { "Fecha", "Entidad", "Promotor/Vendedor", "Responsables", "Categorias",
                "Casetas", "Cantidad casetas", "Comprobante", "Total Bs" };
        for (int i = 0; i < columnas.length; i++) celda(cab, i, columnas[i], estilos.cabecera);

        for (ReporteVentaDTO v : ventas) {
            Row r = hoja.createRow(fila++);
            celda(r, 0, v.fechaCompra() == null ? "-" : FECHA.format(v.fechaCompra()), estilos.normal);
            celda(r, 1, v.entidad(), estilos.normal);
            celda(r, 2, v.promotor(), estilos.normal);
            celda(r, 3, v.responsables(), estilos.normal);
            celda(r, 4, v.categorias(), estilos.normal);
            celda(r, 5, v.casetas(), estilos.normal);
            numero(r, 6, v.cantidadCasetas() == null ? 0 : v.cantidadCasetas(), estilos.numero);
            celda(r, 7, Boolean.TRUE.equals(v.conComprobante()) ? "Si" : "No", estilos.normal);
            decimal(r, 8, v.totalBs(), estilos.moneda);
        }
    }

    private String describirPeriodo(ReporteVentasService.Filtros f) {
        if (f == null) return "Todas las fechas";
        return (f.desde() == null ? "inicio" : f.desde().toString()) + " a "
                + (f.hasta() == null ? "hoy" : f.hasta().toString());
    }

    private void celda(Row fila, int columna, String valor, CellStyle estilo) {
        Cell c = fila.createCell(columna);
        c.setCellValue(valor == null ? "-" : valor);
        c.setCellStyle(estilo);
    }

    private void numero(Row fila, int columna, int valor, CellStyle estilo) {
        Cell c = fila.createCell(columna);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private void decimal(Row fila, int columna, BigDecimal valor, CellStyle estilo) {
        Cell c = fila.createCell(columna);
        c.setCellValue((valor == null ? BigDecimal.ZERO : valor).doubleValue());
        c.setCellStyle(estilo);
    }

    private static BigDecimal total(List<ReporteVentaDTO> ventas) {
        return ventas.stream().map(ReporteVentaDTO::totalBs).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static class Estilos {
        final CellStyle titulo;
        final CellStyle cabecera;
        final CellStyle etiqueta;
        final CellStyle normal;
        final CellStyle numero;
        final CellStyle moneda;

        Estilos(Workbook wb) {
            Font ft = wb.createFont();
            ft.setBold(true);
            ft.setFontHeightInPoints((short) 15);
            ft.setColor(IndexedColors.DARK_GREEN.getIndex());
            titulo = wb.createCellStyle();
            titulo.setFont(ft);

            Font fc = wb.createFont();
            fc.setBold(true);
            fc.setColor(IndexedColors.WHITE.getIndex());
            cabecera = conBorde(wb.createCellStyle());
            cabecera.setFont(fc);
            cabecera.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setAlignment(HorizontalAlignment.CENTER);

            Font fe = wb.createFont();
            fe.setBold(true);
            etiqueta = wb.createCellStyle();
            etiqueta.setFont(fe);

            normal = conBorde(wb.createCellStyle());
            normal.setVerticalAlignment(VerticalAlignment.TOP);
            normal.setWrapText(true);

            numero = conBorde(wb.createCellStyle());
            numero.setAlignment(HorizontalAlignment.RIGHT);

            moneda = conBorde(wb.createCellStyle());
            moneda.setAlignment(HorizontalAlignment.RIGHT);
            moneda.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        }

        private static CellStyle conBorde(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            return s;
        }
    }
}
