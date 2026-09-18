package com.usic.uniFex.model.service;

import java.io.OutputStream;
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

import com.usic.uniFex.model.dto.ResponsableReporteView;

@Service
public class ReporteResponsablesExcelService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void generar(List<ResponsableReporteView> responsables, OutputStream salida) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet hoja = wb.createSheet("Responsables");
            Estilos estilos = new Estilos(wb);
            int fila = 0;

            Row titulo = hoja.createRow(fila++);
            celda(titulo, 0, "REPORTE DE RESPONSABLES POR CATEGORÍA", estilos.titulo);
            fila++;

            agregarTabla(hoja, fila, responsables == null ? List.of() : responsables, estilos);

            for (int i = 0; i < 15; i++) hoja.autoSizeColumn(i);
            hoja.createFreezePane(0, 2);
            wb.write(salida);
        }
    }

    private void agregarTabla(Sheet hoja, int fila, List<ResponsableReporteView> responsables, Estilos estilos) {
        Row cab = hoja.createRow(fila++);
        String[] columnas = {
            "Entidad", "Tipo Entidad", "Objeto", "Categoría", "Puesto",
            "Responsable", "C.I.", "Celular", "Titular", "Extra",
            "Tiene Foto", "Credencial Impresa", "Veces Impreso", "Última Impresión", "Incompleta", "Sin Credencial Impresa"
        };
        for (int i = 0; i < columnas.length; i++) celda(cab, i, columnas[i], estilos.cabecera);

        String entidadActual = null;
        String categoriaActual = null;

        for (ResponsableReporteView r : responsables) {
            Row row = hoja.createRow(fila++);

            String entidad = r.getEntidadNombre() != null ? r.getEntidadNombre() : "";
            String categoria = r.getCategoriaNombre() != null ? r.getCategoriaNombre() : "";
            String puesto = r.getPuestoCodigo() != null ? r.getPuestoCodigo() : "";

            boolean cambioEntidad = !entidad.equals(entidadActual);
            boolean cambioCategoria = !categoria.equals(categoriaActual);

            if (cambioEntidad) {
                entidadActual = entidad;
                categoriaActual = categoria;
            } else if (cambioCategoria) {
                categoriaActual = categoria;
            }

            celda(row, 0, cambioEntidad ? entidad : "", estilos.normal);
            celda(row, 1, r.getTipoEntidadNombre() != null ? r.getTipoEntidadNombre() : "", estilos.normal);
            celda(row, 2, r.getEntidadObjeto() != null ? r.getEntidadObjeto() : "", estilos.normal);
            celda(row, 3, cambioCategoria ? categoria : "", estilos.normal);
            celda(row, 4, puesto, estilos.normal);
            celda(row, 5, r.getNombreCompleto(), estilos.normal);
            celda(row, 6, r.getCi() != null ? r.getCi() : "", estilos.normal);
            celda(row, 7, r.getCelular() != null ? r.getCelular() : "", estilos.normal);
            celda(row, 8, Boolean.TRUE.equals(r.getEsTitular()) ? "SI" : "NO", estilos.normal);
            celda(row, 9, Boolean.TRUE.equals(r.getEsExtra()) ? "SI" : "NO", estilos.normal);
            celda(row, 10, r.getTieneFoto(), estilos.normal);
            celda(row, 11, r.getCredencialImpresa(), estilos.normal);
            celda(row, 12, r.getVecesImpreso() != null ? String.valueOf(r.getVecesImpreso()) : "0", estilos.numero);
            celda(row, 13, r.getUltimaImpresion() != null ? FECHA.format(r.getUltimaImpresion()) : "", estilos.normal);
            celda(row, 14, Boolean.TRUE.equals(r.getAlgunaIncompleta()) ? "SI" : "NO", estilos.normal);
            celda(row, 15, r.getSinCredencialImpresa(), estilos.normal);
        }
    }

    private void celda(Row fila, int columna, String valor, CellStyle estilo) {
        Cell c = fila.createCell(columna);
        c.setCellValue(valor == null ? "" : valor);
        c.setCellStyle(estilo);
    }

    private void numero(Row fila, int columna, int valor, CellStyle estilo) {
        Cell c = fila.createCell(columna);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private static class Estilos {
        final CellStyle titulo;
        final CellStyle cabecera;
        final CellStyle normal;
        final CellStyle numero;

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

            normal = conBorde(wb.createCellStyle());
            normal.setVerticalAlignment(VerticalAlignment.TOP);
            normal.setWrapText(true);

            numero = conBorde(wb.createCellStyle());
            numero.setAlignment(HorizontalAlignment.RIGHT);
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