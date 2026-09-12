package com.usic.uniFex.model.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;

import com.usic.uniFex.model.dto.CredencialDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Imprime credenciales: la plantilla de fondo, el QR y los datos encima.
 *
 * <h2>Por que la disposicion es un DATO y no coordenadas en el codigo</h2>
 * La plantilla va a cambiar —ya hay dos versiones— y cada cambio movería el QR y los campos.
 * Aqui la posicion de cada cosa se expresa en FRACCIONES de la plantilla (0..1), no en
 * milimetros ni en pixeles: asi la misma disposicion vale para una plantilla de 1182 px o de
 * 4000, y para una credencial impresa de 8 cm o de 12. Cambiar de plantilla es cambiar una
 * imagen y cuatro numeros, no tocar el generador.
 *
 * Las fracciones de {@link #CON_ETIQUETAS} estan MEDIDAS sobre la plantilla, no estimadas a
 * ojo: se detectaron las cajas blancas impresas buscando las franjas de blanco puro.
 *
 * <h2>La hoja</h2>
 * Carta, con la credencial centrada y del ancho que se pida en centimetros. Se imprime una por
 * hoja a proposito: es lo que permite recortarla centrada y meterla en un portacredencial sin
 * que el corte de una arruine la de al lado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredencialPdfService {

    /** Un rectangulo dentro de la plantilla, en fracciones de su ancho y su alto. */
    public record Caja(double x, double y, double ancho, double alto) {
    }

    /**
     * Donde va cada cosa sobre una plantilla concreta.
     *
     * @param imagen   recurso de la plantilla dentro del jar
     * @param qr       caja del QR; se usa el ANCHO y sale cuadrado
     * @param nombre   nombre y apellidos del responsable
     * @param empresa  entidad y rubro, juntos
     * @param ci       cedula de identidad
     * @param codigo   numeros de caseta, con la categoria debajo en letra pequeña
     * @param mayusculas si el texto se imprime en mayusculas (como el resto del sistema)
     *
     * Cualquier caja puede ser {@code null}: ese campo simplemente no se dibuja. Hace falta
     * porque la plantilla de QR grande no lleva ni nombre ni entidad ni C.I. — ahi el dato lo
     * da el QR, y el papel solo tiene que decir de que caseta es.
     */
    public record Disposicion(String imagen, Caja qr, Caja nombre, Caja empresa,
                              Caja ci, Caja codigo, boolean mayusculas) {
    }

    /**
     * Plantilla con las etiquetas ya impresas ("NOMBRE Y APELLIDO", "EMPRESA / SERVICIO"...).
     * Los valores se dibujan en la MITAD INFERIOR de cada caja, debajo de su etiqueta.
     */
    public static final Disposicion CON_ETIQUETAS = new Disposicion(
            "static/assets/CREDENCIAL_4.jpg",
            // Centrado en el recuadro claro de arriba a la derecha, que va de 0.685 a 0.947.
            new Caja(0.703, 0.050, 0.225, 0.225),
            new Caja(0.165, 0.502, 0.758, 0.048),
            new Caja(0.165, 0.608, 0.758, 0.048),
            new Caja(0.165, 0.713, 0.431, 0.048),
            new Caja(0.658, 0.713, 0.182, 0.048),
            true);

    /**
     * Misma plantilla sin etiquetas impresas, con el QR grande y centrado un poco por encima
     * del medio. Los datos ocupan las cajas enteras, porque aqui no hay etiqueta que estorbe.
     */
    public static final Disposicion QR_GRANDE = new Disposicion(
            "static/assets/CREDENCIAL3.jpg",
            // Lo mas grande que cabe entre el logotipo de FEXPO y la primera caja blanca.
            // El alto que ocupa sale de la proporcion de la plantilla, no de este numero:
            // 0.375 de ancho son 0.289 de alto, asi que termina justo encima de la caja.
            new Caja(0.3125, 0.170, 0.375, 0.375),
            null,   // sin nombre
            null,   // sin entidad ni rubro
            null,   // sin C.I.
            // Lo unico escrito, dentro de la PRIMERA caja de la plantilla y centrado: la
            // caseta grande con su categoria debajo. Quien necesite saber QUIEN es, escanea
            // el QR; el papel solo dice DE DONDE es.
            new Caja(0.147, 0.474, 0.794, 0.076),
            true);

    public static Disposicion porNombre(String nombre) {
        return "QR_GRANDE".equalsIgnoreCase(nombre) ? QR_GRANDE : CON_ETIQUETAS;
    }

    /** Ancho impreso por defecto. Con la proporcion de la plantilla da unos 13 cm de alto. */
    public static final double ANCHO_CM_POR_DEFECTO = 10.0;
    private static final float CM = 72f / 2.54f;   // puntos PostScript por centimetro

    /**
     * @param credenciales lo que se imprime, una por hoja
     * @param disposicion  donde va cada cosa
     * @param anchoCm      ancho impreso de la credencial; el alto sale de la proporcion
     * @param urlBase      raiz publica para el QR (ej. https://virtual.uap.edu.bo:8070)
     */
    public byte[] generar(List<CredencialDTO> credenciales, Disposicion disposicion,
                          double anchoCm, String urlBase, CredencialCodigoService codigos) {
        if (credenciales == null || credenciales.isEmpty()) {
            throw new IllegalArgumentException("No hay credenciales que imprimir");
        }
        double ancho = Math.max(4.0, Math.min(20.0, anchoCm));   // fuera de ahi no entra en carta

        Document doc = new Document(PageSize.LETTER, 0, 0, 0, 0);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(
                    BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            Image plantilla = cargarPlantilla(disposicion.imagen());

            for (int i = 0; i < credenciales.size(); i++) {
                if (i > 0) doc.newPage();
                pintar(lienzo, fuente, plantilla, disposicion, credenciales.get(i),
                        ancho, urlBase, codigos);
            }
            doc.close();
            log.info("Credenciales impresas: {} (plantilla {}, {} cm de ancho)",
                    credenciales.size(), disposicion.imagen(), ancho);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de credenciales: "
                    + e.getMessage(), e);
        }
    }

    private void pintar(PdfContentByte lienzo, BaseFont fuente, Image plantilla,
                        Disposicion d, CredencialDTO c, double anchoCm, String urlBase,
                        CredencialCodigoService codigos) throws Exception {

        Rectangle hoja = PageSize.LETTER;
        float w = (float) (anchoCm * CM);
        float h = w * plantilla.getHeight() / plantilla.getWidth();
        // Centrada en la hoja: es lo que permite recortarla y que quede igual por los cuatro lados.
        float x0 = (hoja.getWidth() - w) / 2f;
        float y0 = (hoja.getHeight() - h) / 2f;

        Image fondo = Image.getInstance(plantilla);
        fondo.scaleAbsolute(w, h);
        fondo.setAbsolutePosition(x0, y0);
        lienzo.addImage(fondo);

        // ---- QR ----
        // El eje Y del PDF crece hacia ARRIBA y el de la disposicion hacia abajo (como la
        // imagen), asi que se invierte aqui una sola vez y el resto del codigo no se entera.
        float lado = (float) (d.qr().ancho() * w);
        float qrX = x0 + (float) (d.qr().x() * w);
        float qrY = y0 + h - (float) (d.qr().y() * h) - lado;
        BarcodeQRCode qr = new BarcodeQRCode(
                codigos.urlPublica(urlBase, c.codigo()), 1000, 1000, null);
        Image imgQr = qr.getImage();
        imgQr.scaleAbsolute(lado, lado);
        imgQr.setAbsolutePosition(qrX, qrY);
        lienzo.addImage(imgQr);

        // ---- Datos ----
        String empresa = c.entidad();
        if (c.rubro() != null && !c.rubro().isBlank()) empresa += "  ·  " + c.rubro();

        texto(lienzo, fuente, d.nombre(), valor(c.nombre(), d), x0, y0, w, h);
        texto(lienzo, fuente, d.empresa(), valor(empresa, d), x0, y0, w, h);
        texto(lienzo, fuente, d.ci(), valor(c.ci(), d), x0, y0, w, h);
        // La caseta con su categoria debajo: el numero solo no dice nada si hay trece zonas.
        codigoConCategoria(lienzo, fuente, d.codigo(), valor(c.casetas(), d),
                valor(c.categoria(), d), x0, y0, w, h, d == QR_GRANDE);
    }

    /**
     * El numero de caseta y, debajo en letra pequeña, su categoria.
     *
     * Van juntos porque por separado ninguno sirve: "17" no dice nada cuando hay trece zonas,
     * y la zona sin el numero tampoco. En la plantilla de QR grande es lo UNICO escrito, asi
     * que ahi va centrado y ocupando todo el ancho.
     */
    private void codigoConCategoria(PdfContentByte lienzo, BaseFont fuente, Caja caja,
                                    String codigo, String categoria, float x0, float y0,
                                    float w, float h, boolean centrado) {
        if (caja == null || codigo == null || codigo.isBlank()) return;

        float cajaW = (float) (caja.ancho() * w);
        float cajaH = (float) (caja.alto() * h);
        float x = x0 + (float) (caja.x() * w);
        float y = y0 + h - (float) (caja.y() * h) - cajaH;
        boolean hayCategoria = categoria != null && !categoria.isBlank();

        // Dos tercios del alto para el numero, un tercio para la categoria. Sin categoria, el
        // numero se queda con todo.
        float altoNumero = hayCategoria ? cajaH * 0.62f : cajaH;
        float tamNumero = ajustar(fuente, codigo, cajaW, altoNumero * 0.92f, altoNumero * 0.35f);
        int alineacion = centrado ? Element.ALIGN_CENTER : Element.ALIGN_LEFT;
        float xTexto = centrado ? x + cajaW / 2f : x;

        lienzo.beginText();
        lienzo.setFontAndSize(fuente, tamNumero);
        lienzo.setColorFill(BaseColor.BLACK);
        lienzo.showTextAligned(alineacion, codigo, xTexto,
                y + cajaH - altoNumero + (altoNumero - tamNumero * 0.70f) / 2f, 0);
        lienzo.endText();

        if (!hayCategoria) return;
        float altoCat = cajaH - altoNumero;
        float tamCat = ajustar(fuente, categoria, cajaW, altoCat * 0.86f, altoCat * 0.30f);
        lienzo.beginText();
        lienzo.setFontAndSize(fuente, tamCat);
        lienzo.setColorFill(new BaseColor(70, 70, 70));
        lienzo.showTextAligned(alineacion, categoria, xTexto,
                y + (altoCat - tamCat * 0.70f) / 2f, 0);
        lienzo.endText();
    }

    /** El mayor tamaño entre `maximo` y `minimo` con el que el texto cabe en `ancho`. */
    private static float ajustar(BaseFont fuente, String texto, float ancho,
                                 float maximo, float minimo) {
        float t = maximo;
        while (t > minimo && fuente.getWidthPoint(texto, t) > ancho) t -= 0.5f;
        return t;
    }

    private static String valor(String s, Disposicion d) {
        String t = s == null ? "" : s.trim();
        return d.mayusculas() ? t.toUpperCase() : t;
    }

    /**
     * Escribe un texto dentro de su caja, centrado en vertical y alineado a la izquierda.
     *
     * El tamaño se ENCOGE hasta que quepa. Hace falta de verdad: los nombres de entidad van de
     * "PIL" a "ASOCIACION DE PRODUCTORES AGROPECUARIOS DEL NORTE INTEGRADO", y sin esto el
     * texto largo se saldria de la credencial por el lado derecho.
     */
    private void texto(PdfContentByte lienzo, BaseFont fuente, Caja caja, String contenido,
                       float x0, float y0, float w, float h) {
        if (caja == null || contenido == null || contenido.isBlank()) return;

        float cajaW = (float) (caja.ancho() * w);
        float cajaH = (float) (caja.alto() * h);
        float x = x0 + (float) (caja.x() * w);
        float y = y0 + h - (float) (caja.y() * h) - cajaH;

        float tam = cajaH * 0.72f;                 // punto de partida: casi todo el alto
        float minimo = cajaH * 0.32f;              // por debajo no se lee de lejos
        while (tam > minimo && fuente.getWidthPoint(contenido, tam) > cajaW) {
            tam -= 0.5f;
        }
        // Si ni al minimo entra, se recorta con puntos suspensivos antes que desbordar.
        String pintable = contenido;
        if (fuente.getWidthPoint(pintable, tam) > cajaW) {
            while (pintable.length() > 4 && fuente.getWidthPoint(pintable + "…", tam) > cajaW) {
                pintable = pintable.substring(0, pintable.length() - 1);
            }
            pintable = pintable + "…";
        }

        lienzo.beginText();
        lienzo.setFontAndSize(fuente, tam);
        lienzo.setColorFill(BaseColor.BLACK);
        // El descuento de 0.30 centra opticamente la linea: la altura de una mayuscula es
        // ~0.7 del tamaño de fuente, no el tamaño entero.
        lienzo.showTextAligned(Element.ALIGN_LEFT, pintable, x, y + (cajaH - tam * 0.70f) / 2f, 0);
        lienzo.endText();
    }

    private Image cargarPlantilla(String recurso) throws Exception {
        try (InputStream in = new ClassPathResource(recurso).getInputStream()) {
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se encontro la plantilla de credencial: " + recurso, e);
        }
    }
}
