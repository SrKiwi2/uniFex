package com.usic.uniFex.model.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.uniFex.model.dto.ReporteVentaDTO;

@Service
public class ReporteVentasPdfService {

    private static final BaseColor VERDE = new BaseColor(21, 128, 61);
    private static final BaseColor VERDE_SUAVE = new BaseColor(220, 252, 231);
    private static final BaseColor TINTA = new BaseColor(17, 24, 39);
    private static final BaseColor MUTED = new BaseColor(107, 114, 128);
    private static final BaseColor BORDE = new BaseColor(209, 213, 219);
    private static final BaseColor FONDO = new BaseColor(249, 250, 251);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void generar(List<ReporteVentaDTO> ventas, ReporteVentasService.Filtros filtros,
                        OutputStream salida) throws Exception {
        Document doc = new Document(PageSize.LETTER.rotate(), 28, 28, 24, 24);
        PdfWriter.getInstance(doc, salida);
        doc.open();
        agregarCabecera(doc, ventas, filtros);
        agregarTabla(doc, ventas == null ? List.of() : ventas);
        agregarPie(doc);
        doc.close();
    }

    private void agregarCabecera(Document doc, List<ReporteVentaDTO> ventas,
                                 ReporteVentasService.Filtros filtros) throws Exception {
        PdfPTable cab = new PdfPTable(new float[] { 14, 62, 24 });
        cab.setWidthPercentage(100);
        cab.setSpacingAfter(10);
        Image logo = leerLogo();
        if (logo != null) {
            logo.scaleToFit(76, 58);
            PdfPCell cLogo = new PdfPCell(logo, false);
            cLogo.setBorder(Rectangle.NO_BORDER);
            cLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cab.addCell(cLogo);
        } else cab.addCell(sinBorde(""));

        Paragraph titulo = new Paragraph();
        titulo.add(new Phrase("REPORTE DE VENTAS REALIZADAS\n", fuente(18, Font.BOLD, TINTA)));
        titulo.add(new Phrase("FEXPO UAP - Universidad Amazonica de Pando\n", fuente(9, Font.BOLD, MUTED)));
        titulo.add(new Phrase(describirFiltros(filtros), fuente(8, Font.NORMAL, MUTED)));
        PdfPCell cTitulo = new PdfPCell(titulo);
        cTitulo.setBorder(Rectangle.NO_BORDER);
        cTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cab.addCell(cTitulo);

        Paragraph resumen = new Paragraph();
        resumen.setAlignment(Element.ALIGN_RIGHT);
        resumen.add(new Phrase(money(total(ventas)) + " Bs\n", fuente(15, Font.BOLD, VERDE)));
        resumen.add(new Phrase((ventas == null ? 0 : ventas.size()) + " ventas", fuente(8, Font.BOLD, MUTED)));
        PdfPCell cResumen = new PdfPCell(resumen);
        cResumen.setBorder(Rectangle.NO_BORDER);
        cResumen.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cab.addCell(cResumen);
        doc.add(cab);
    }

    private void agregarTabla(Document doc, List<ReporteVentaDTO> ventas) throws Exception {
        PdfPTable tabla = new PdfPTable(new float[] { 8, 18, 15, 22, 15, 12, 7, 10 });
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String h : List.of("Fecha", "Entidad", "Promotor/Vendedor", "Responsables", "Categorias", "Casetas", "Comp.", "Total")) {
            encabezado(tabla, h);
        }
        int i = 0;
        for (ReporteVentaDTO v : ventas) {
            boolean par = ++i % 2 == 0;
            celda(tabla, v.fechaCompra() == null ? "-" : FECHA.format(v.fechaCompra()), par, Element.ALIGN_LEFT, fuente(7, Font.NORMAL, TINTA));
            celda(tabla, v.entidad(), par, Element.ALIGN_LEFT, fuente(7, Font.BOLD, TINTA));
            celda(tabla, v.promotor(), par, Element.ALIGN_LEFT, fuente(7, Font.NORMAL, TINTA));
            celda(tabla, v.responsables(), par, Element.ALIGN_LEFT, fuente(6.5f, Font.NORMAL, MUTED));
            celda(tabla, v.categorias(), par, Element.ALIGN_LEFT, fuente(7, Font.NORMAL, TINTA));
            celda(tabla, v.casetas(), par, Element.ALIGN_LEFT, fuente(7, Font.NORMAL, TINTA));
            celda(tabla, Boolean.TRUE.equals(v.conComprobante()) ? "Si" : "No", par, Element.ALIGN_CENTER, fuente(7, Font.BOLD, Boolean.TRUE.equals(v.conComprobante()) ? VERDE : MUTED));
            celda(tabla, "Bs " + money(v.totalBs()), par, Element.ALIGN_RIGHT, fuente(7, Font.BOLD, VERDE));
        }
        doc.add(tabla);
    }

    private String describirFiltros(ReporteVentasService.Filtros f) {
        if (f == null) return "Todas las fechas";
        String rango = (f.desde() == null ? "inicio" : f.desde().toString()) + " a "
                + (f.hasta() == null ? "hoy" : f.hasta().toString());
        return "Periodo: " + rango;
    }

    private void agregarPie(Document doc) throws Exception {
        Paragraph pie = new Paragraph("Generado el " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())
                + " - Sistema automatizado UniFex", fuente(7, Font.NORMAL, MUTED));
        pie.setSpacingBefore(10);
        pie.setAlignment(Element.ALIGN_RIGHT);
        doc.add(pie);
    }

    private void encabezado(PdfPTable tabla, String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente(7.5f, Font.BOLD, com.itextpdf.text.BaseColor.WHITE)));
        c.setBackgroundColor(VERDE);
        c.setBorderColor(VERDE);
        c.setPadding(6);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabla.addCell(c);
    }

    private void celda(PdfPTable tabla, String texto, boolean par, int alineacion, Font fuente) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "-" : texto, fuente));
        c.setBackgroundColor(par ? FONDO : com.itextpdf.text.BaseColor.WHITE);
        c.setBorderColor(BORDE);
        c.setPadding(5);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(c);
    }

    private Image leerLogo() {
        try (InputStream in = new ClassPathResource("static/assets/img/logo/EXPO.png").getInputStream()) {
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }

    private PdfPCell sinBorde(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto));
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static Font fuente(float tam, int estilo, BaseColor color) {
        return new Font(Font.FontFamily.HELVETICA, tam, estilo, color);
    }

    private static BigDecimal total(List<ReporteVentaDTO> ventas) {
        if (ventas == null) return BigDecimal.ZERO;
        return ventas.stream().map(ReporteVentaDTO::totalBs).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String money(BigDecimal n) {
        return (n == null ? BigDecimal.ZERO : n).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
