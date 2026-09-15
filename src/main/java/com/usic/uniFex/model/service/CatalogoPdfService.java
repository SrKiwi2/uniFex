package com.usic.uniFex.model.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
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
import com.usic.uniFex.model.entity.Categoria;

/** Reporte PDF del catalogo de precios de casetas. */
@Service
public class CatalogoPdfService {

    private static final BaseColor VERDE = new BaseColor(21, 128, 61);
    private static final BaseColor VERDE_SUAVE = new BaseColor(220, 252, 231);
    private static final BaseColor TINTA = new BaseColor(17, 24, 39);
    private static final BaseColor MUTED = new BaseColor(107, 114, 128);
    private static final BaseColor BORDE = new BaseColor(209, 213, 219);
    private static final BaseColor FONDO = new BaseColor(249, 250, 251);

    public void generar(List<Categoria> categorias, OutputStream salida) throws Exception {
        Document doc = new Document(PageSize.LETTER, 36, 36, 34, 34);
        PdfWriter.getInstance(doc, salida);
        doc.open();

        agregarCabecera(doc, categorias == null ? 0 : categorias.size());
        agregarTabla(doc, categorias == null ? List.of() : categorias);
        agregarPie(doc);

        doc.close();
    }

    private void agregarCabecera(Document doc, int cantidad) throws Exception {
        PdfPTable cab = new PdfPTable(new float[] { 18, 58, 24 });
        cab.setWidthPercentage(100);
        cab.setSpacingAfter(14);

        Image logo = leerLogo();
        if (logo != null) {
            logo.scaleToFit(78, 62);
            PdfPCell cLogo = new PdfPCell(logo, false);
            cLogo.setBorder(Rectangle.NO_BORDER);
            cLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cab.addCell(cLogo);
        } else {
            cab.addCell(sinBorde(""));
        }

        Paragraph titulo = new Paragraph();
        titulo.add(new Phrase("CATALOGO DE PRECIOS\n", fuente(18, Font.BOLD, TINTA)));
        titulo.add(new Phrase("FEXPO UAP - Universidad Amazonica de Pando\n", fuente(9, Font.BOLD, MUTED)));
        titulo.add(new Phrase("Precios base por categoria de caseta", fuente(8, Font.NORMAL, MUTED)));
        PdfPCell cTitulo = new PdfPCell(titulo);
        cTitulo.setBorder(Rectangle.NO_BORDER);
        cTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cab.addCell(cTitulo);

        Paragraph resumen = new Paragraph();
        resumen.setAlignment(Element.ALIGN_RIGHT);
        resumen.add(new Phrase(cantidad + "\n", fuente(22, Font.BOLD, VERDE)));
        resumen.add(new Phrase("categorias", fuente(8, Font.BOLD, MUTED)));
        PdfPCell cResumen = new PdfPCell(resumen);
        cResumen.setBorder(Rectangle.NO_BORDER);
        cResumen.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cab.addCell(cResumen);

        doc.add(cab);
    }

    private void agregarTabla(Document doc, List<Categoria> categorias) throws Exception {
        PdfPTable tabla = new PdfPTable(new float[] { 7, 34, 12, 29, 18 });
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        encabezado(tabla, "#");
        encabezado(tabla, "Categoria");
        encabezado(tabla, "Color");
        encabezado(tabla, "Descripcion");
        encabezado(tabla, "Precio base");

        int i = 1;
        for (Categoria c : categorias) {
            boolean par = i % 2 == 0;
            celda(tabla, String.valueOf(i), par, Element.ALIGN_CENTER, fuente(8, Font.NORMAL, TINTA));
            celda(tabla, nvl(c.getNombre()), par, Element.ALIGN_LEFT, fuente(8, Font.BOLD, TINTA));
            celdaColor(tabla, c.getColor(), par);
            celda(tabla, nvl(c.getDescripcion()), par, Element.ALIGN_LEFT, fuente(7.5f, Font.NORMAL, MUTED));
            celda(tabla, "Bs " + money(c.getPrecioBase()), par, Element.ALIGN_RIGHT, fuente(8.5f, Font.BOLD, VERDE));
            i++;
        }
        doc.add(tabla);
    }

    private void agregarPie(Document doc) throws Exception {
        Paragraph pie = new Paragraph(
                "Generado el " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())
                        + " - Sistema automatizado UniFex",
                fuente(7, Font.NORMAL, MUTED));
        pie.setSpacingBefore(12);
        pie.setAlignment(Element.ALIGN_RIGHT);
        doc.add(pie);
    }

    private Image leerLogo() {
        try (InputStream in = new ClassPathResource("static/assets/img/logo/EXPO.png").getInputStream()) {
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }

    private void encabezado(PdfPTable tabla, String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente(8, Font.BOLD, BaseColor.WHITE)));
        c.setBackgroundColor(VERDE);
        c.setBorderColor(VERDE);
        c.setPadding(7);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabla.addCell(c);
    }

    private void celda(PdfPTable tabla, String texto, boolean par, int alineacion, Font fuente) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente));
        c.setBackgroundColor(par ? FONDO : BaseColor.WHITE);
        c.setBorderColor(BORDE);
        c.setPadding(6);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(c);
    }

    private void celdaColor(PdfPTable tabla, String color, boolean par) {
        PdfPCell c = new PdfPCell(new Phrase(""));
        c.setBackgroundColor(par ? FONDO : BaseColor.WHITE);
        c.setBorderColor(BORDE);
        c.setPadding(6);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (color != null && color.matches("#[0-9a-fA-F]{6}")) {
            c.setBackgroundColor(new BaseColor(
                    Integer.parseInt(color.substring(1, 3), 16),
                    Integer.parseInt(color.substring(3, 5), 16),
                    Integer.parseInt(color.substring(5, 7), 16)));
        }
        tabla.addCell(c);
    }

    private PdfPCell sinBorde(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto));
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static Font fuente(float tam, int estilo, BaseColor color) {
        return new Font(Font.FontFamily.HELVETICA, tam, estilo, color);
    }

    private static String money(BigDecimal n) {
        return (n == null ? BigDecimal.ZERO : n).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "-" : s.trim();
    }
}
