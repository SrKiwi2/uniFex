package com.usic.uniFex.model.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * Convierte una tabla ya calculada en PDF o en Excel.
 *
 * Existe para no escribir cuatro servicios casi iguales. Los reportes de direccion son cuatro
 * hoy y seran mas: lo unico que cambia entre ellos son el titulo, las cabeceras y las filas, y
 * duplicar el andamiaje de iText y POI por cada uno garantiza que al sexto ya no se parezcan
 * entre si —tipos distintos, margenes distintos, y un fallo arreglado en uno y no en los otros.
 *
 * <b>Recibe TEXTO ya formateado, no numeros.</b> Es deliberado: quien calcula el reporte sabe si
 * una columna son bolivianos con dos decimales, un porcentaje o un conteo; este servicio no, y
 * adivinarlo aqui daria "1200" donde debe decir "1.200,00 Bs". La unica excepcion es Excel, que
 * escribe como NUMERO lo que venga marcado como tal: una hoja de calculo con importes en texto
 * no deja sumar ni ordenar, que es justo para lo que se pide en Excel.
 *
 * Varias tablas van a un solo archivo: en Excel, una hoja por tabla; en PDF, una tras otra,
 * cada una en su pagina. Es lo que pide un reporte como el de Control de ventas, donde las
 * ventas, sus casetas, sus pagos y sus entregas se leen juntos.
 */
@Service
public class TablaExportService {

    private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Como se pinta una columna. `NUMERO` y `ENTERO` van a la derecha y en Excel se escriben
     * numericos; `ENTERO` sin decimales, para que tres casetas no salgan como "3,00".
     */
    public enum Tipo { TEXTO, NUMERO, ENTERO }

    public record Columna(String titulo, Tipo tipo) {
        public static Columna texto(String t) {
            return new Columna(t, Tipo.TEXTO);
        }

        public static Columna numero(String t) {
            return new Columna(t, Tipo.NUMERO);
        }

        public static Columna entero(String t) {
            return new Columna(t, Tipo.ENTERO);
        }

        boolean esNumerica() {
            return tipo != Tipo.TEXTO;
        }
    }

    /**
     * Una tabla lista para exportar.
     *
     * {@code notas} son las lineas que van bajo el titulo: de donde salen los numeros y que
     * NO incluyen. Un reporte sin esa letra pequeña se interpreta mal —"¿esto cuenta las
     * ventas canceladas?"— y la respuesta tiene que viajar con el papel, no en la cabeza de
     * quien lo genero.
     */
    public record Tabla(String titulo, List<String> notas, List<Columna> columnas,
                        List<List<String>> filas) {
    }

    // ------------------------------------------------------------------ PDF

    public byte[] pdf(Tabla tabla) {
        return pdf(List.of(tabla));
    }

    /** Varias tablas en un PDF, cada una empezando en su pagina. */
    public byte[] pdf(List<Tabla> tablas) {
        // Apaisado: estas tablas tienen siete u ocho columnas y en vertical se aprietan hasta
        // partir los nombres de entidad en tres lineas.
        Document doc = new Document(PageSize.LETTER.rotate(), 28, 28, 28, 28);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            boolean primera = true;
            for (Tabla tabla : tablas) {
                if (!primera) doc.newPage();
                primera = false;
                pdfTabla(doc, tabla);
            }
            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private void pdfTabla(Document doc, Tabla tabla) throws Exception {
        Font fTitulo = new Font(Font.FontFamily.HELVETICA, 15, Font.BOLD);
        Font fNota = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.DARK_GRAY);
        Paragraph titulo = new Paragraph(tabla.titulo(), fTitulo);
        titulo.setSpacingAfter(4f);
        doc.add(titulo);

        doc.add(new Paragraph("Generado el " + LocalDateTime.now().format(MOMENTO), fNota));
        for (String n : tabla.notas() == null ? List.<String>of() : tabla.notas()) {
            doc.add(new Paragraph(n, fNota));
        }

        // El texto pesa el doble que un numero: con anchos iguales, un nombre de tres palabras
        // se partia en cuatro lineas junto a una columna de importes medio vacia.
        float[] anchos = new float[tabla.columnas().size()];
        for (int i = 0; i < anchos.length; i++) anchos[i] = tabla.columnas().get(i).esNumerica() ? 1f : 2f;
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        t.setSpacingBefore(10f);
        // La cabecera se repite en cada pagina: sin esto, a partir de la segunda hoja las
        // columnas son numeros sin nombre.
        t.setHeaderRows(1);

        Font fCab = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);
        for (Columna c : tabla.columnas()) {
            PdfPCell cel = new PdfPCell(new Phrase(c.titulo(), fCab));
            cel.setBackgroundColor(new BaseColor(37, 99, 235));
            cel.setPadding(5f);
            cel.setHorizontalAlignment(c.esNumerica() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            t.addCell(cel);
        }

        Font fDato = new Font(Font.FontFamily.HELVETICA, 8.5f);
        int n = 0;
        for (List<String> fila : tabla.filas()) {
            // Filas alternas: con ocho columnas apaisadas, seguir una fila con la vista sin
            // el rayado es como se leen mal los numeros.
            BaseColor fondo = (n++ % 2 == 0) ? BaseColor.WHITE : new BaseColor(243, 244, 246);
            for (int i = 0; i < tabla.columnas().size(); i++) {
                String v = i < fila.size() ? fila.get(i) : "";
                PdfPCell cel = new PdfPCell(new Phrase(v == null ? "" : v, fDato));
                cel.setBackgroundColor(fondo);
                cel.setPadding(4f);
                cel.setBorderColor(new BaseColor(209, 213, 219));
                cel.setHorizontalAlignment(tabla.columnas().get(i).esNumerica()
                        ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                t.addCell(cel);
            }
        }
        doc.add(t);

        if (tabla.filas().isEmpty()) {
            // Un PDF con la tabla vacia y sin una palabra parece que fallo la descarga.
            doc.add(new Paragraph("No hay datos para este reporte.", fNota));
        }
    }

    // ------------------------------------------------------------------ Excel

    public byte[] excel(Tabla tabla) {
        return excel(List.of(tabla));
    }

    /** Varias tablas en un libro, una hoja por tabla, en el orden recibido. */
    public byte[] excel(List<Tabla> tablas) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle cabecera = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font fCab = wb.createFont();
            fCab.setBold(true);
            fCab.setColor(IndexedColors.WHITE.getIndex());
            cabecera.setFont(fCab);
            cabecera.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setBorderBottom(BorderStyle.THIN);
            cabecera.setAlignment(HorizontalAlignment.CENTER);

            CellStyle numero = wb.createCellStyle();
            numero.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle entero = wb.createCellStyle();
            entero.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            Set<String> nombres = new HashSet<>();
            for (Tabla tabla : tablas) {
                // El nombre de la hoja no admite : \ / ? * [ ] ni mas de 31 caracteres, y Excel
                // rechaza el archivo entero si se pasa: se limpia aqui en vez de confiar en que
                // ningun titulo los lleve nunca. Y tiene que ser unico dentro del libro.
                Sheet hoja = wb.createSheet(nombreUnico(nombreHoja(tabla.titulo()), nombres));
                excelHoja(hoja, tabla, cabecera, numero, entero);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el Excel: " + e.getMessage(), e);
        }
    }

    private void excelHoja(Sheet hoja, Tabla tabla, CellStyle cabecera, CellStyle numero, CellStyle entero) {
        int f = 0;
        Row rTitulo = hoja.createRow(f++);
        rTitulo.createCell(0).setCellValue(tabla.titulo());
        hoja.createRow(f++).createCell(0)
                .setCellValue("Generado el " + LocalDateTime.now().format(MOMENTO));
        for (String n : tabla.notas() == null ? List.<String>of() : tabla.notas()) {
            hoja.createRow(f++).createCell(0).setCellValue(n);
        }
        f++; // una fila en blanco entre la cabecera del informe y la tabla

        Row rCab = hoja.createRow(f++);
        for (int i = 0; i < tabla.columnas().size(); i++) {
            Cell c = rCab.createCell(i);
            c.setCellValue(tabla.columnas().get(i).titulo());
            c.setCellStyle(cabecera);
        }

        for (List<String> fila : tabla.filas()) {
            Row r = hoja.createRow(f++);
            for (int i = 0; i < tabla.columnas().size(); i++) {
                Cell c = r.createCell(i);
                String v = i < fila.size() ? fila.get(i) : "";
                Columna col = tabla.columnas().get(i);
                Double n = col.esNumerica() ? aNumero(v) : null;
                if (n != null) {
                    // Numero de verdad, no texto: en Excel se pide para SUMAR y ordenar, y
                    // una columna de importes en texto no deja hacer ni lo uno ni lo otro.
                    c.setCellValue(n);
                    c.setCellStyle(col.tipo() == Tipo.ENTERO ? entero : numero);
                } else {
                    c.setCellValue(v == null ? "" : v);
                }
            }
        }

        // Se congela la fila de cabeceras: una tabla de doscientas filas se recorre y sin
        // esto se pierde de vista que columna es cual.
        hoja.createFreezePane(0, f - tabla.filas().size());
        for (int i = 0; i < tabla.columnas().size(); i++) {
            hoja.autoSizeColumn(i);
            // Un tope: una celda con los doce responsables de una venta haria una columna de
            // media pantalla y empujaria el resto fuera de la vista.
            if (hoja.getColumnWidth(i) > 60 * 256) hoja.setColumnWidth(i, 60 * 256);
        }
    }

    /**
     * El texto de una celda como numero, o null si no lo es.
     *
     * Deshace el formato con el que vino ("1.234,50 Bs" -> 1234.5): los separadores son los de
     * es-BO, donde el punto agrupa y la coma decide los decimales. Al reves —interpretando el
     * punto como decimal— 1.234,50 se convertiria en 1,23 y el Excel mentiria por mil.
     */
    private static Double aNumero(String v) {
        if (v == null) return null;
        String limpio = v.replace("Bs", "").replace("%", "").trim()
                .replace(".", "").replace(',', '.');
        if (limpio.isEmpty()) return null;
        try {
            return Double.valueOf(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nombreHoja(String titulo) {
        String s = (titulo == null ? "Reporte" : titulo).replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        if (s.isEmpty()) s = "Reporte";
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    /** Excel no admite dos hojas con el mismo nombre (sin distinguir mayusculas). */
    private static String nombreUnico(String base, Set<String> usados) {
        String nombre = base;
        for (int i = 2; !usados.add(nombre.toLowerCase(java.util.Locale.ROOT)); i++) {
            String sufijo = " (" + i + ")";
            nombre = (base.length() + sufijo.length() > 31 ? base.substring(0, 31 - sufijo.length()) : base) + sufijo;
        }
        return nombre;
    }
}
