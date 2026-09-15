package com.usic.uniFex.model.service;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
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
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.dto.PlantillaCredencial.Caja;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Imprime credenciales: la plantilla de fondo, el QR y los datos encima.
 *
 * <h2>Aqui no se declara ninguna plantilla</h2>
 * Que plantillas hay y donde va cada cosa en ellas vive en {@link PlantillaCredencial}, que es
 * el unico archivo que hay que tocar para añadir una. Este servicio solo sabe dibujar: recibe
 * una disposicion en fracciones (0..1) y la pinta al tamaño que se le pida. Cambiar de plantilla
 * es cambiar una imagen y cuatro numeros, no tocar el generador.
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

    /** Ancho impreso por defecto. Con la proporcion de la plantilla da unos 13 cm de alto. */
    public static final double ANCHO_CM_POR_DEFECTO = 10.0;
    private static final float CM = 72f / 2.54f;   // puntos PostScript por centimetro

    /**
     * Los bytes de cada plantilla, leidos del jar una sola vez.
     *
     * Se cachean los BYTES y no la {@link Image} de iText: una imagen ya construida arrastra la
     * referencia indirecta del documento en el que se uso, y compartirla entre dos PDF a la vez
     * es pedir un PDF corrupto. Construir la imagen desde bytes ya en memoria es barato; leer
     * 1,2 MB del classpath en cada peticion, no.
     */
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    /** Donde viven las fotos subidas. Es la misma raiz que sirve /files/**. */
    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    /**
     * Alto / ancho de la plantilla, que es la proporcion con la que se imprime.
     *
     * La necesita la pantalla para dibujar la vista previa sobre la hoja carta. Sale de la
     * imagen real, no de un numero escrito a mano: una plantilla nueva con otras medidas no
     * puede dejar la vista previa mintiendo.
     */
    public double proporcion(PlantillaCredencial plantilla) {
        try {
            Image img = Image.getInstance(bytes(plantilla.imagen()));
            return img.getHeight() / img.getWidth();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo leer la plantilla " + plantilla.id() + ": " + e.getMessage(), e);
        }
    }

    /**
     * @param credenciales lo que se imprime, una por hoja
     * @param plantilla    la plantilla y donde va cada cosa en ella
     * @param anchoCm      ancho impreso de la credencial; el alto sale de la proporcion
     * @param urlBase      raiz publica para el QR (ej. https://virtual.uap.edu.bo:8070)
     */
    public byte[] generar(List<CredencialDTO> credenciales, PlantillaCredencial plantilla,
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
            // Fuera del bucle a proposito: iText incrusta la imagen UNA vez y las copias de cada
            // pagina comparten esa incrustacion. Movida aqui dentro, 800 credenciales pesarian
            // 800 x 1,2 MB en vez de 3,5 MB.
            Image fondoOriginal = Image.getInstance(bytes(plantilla.imagen()));

            for (int i = 0; i < credenciales.size(); i++) {
                if (i > 0) doc.newPage();
                pintar(lienzo, fuente, fondoOriginal, plantilla, credenciales.get(i),
                        ancho, urlBase, codigos);
            }
            doc.close();
            log.info("Credenciales impresas: {} (plantilla {}, {} cm de ancho)",
                    credenciales.size(), plantilla.id(), ancho);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de credenciales: "
                    + e.getMessage(), e);
        }
    }

    private void pintar(PdfContentByte lienzo, BaseFont fuente, Image plantillaImg,
                        PlantillaCredencial d, CredencialDTO c, double anchoCm, String urlBase,
                        CredencialCodigoService codigos) throws Exception {

        Rectangle hoja = PageSize.LETTER;
        float w = (float) (anchoCm * CM);
        float h = w * plantillaImg.getHeight() / plantillaImg.getWidth();
        // Centrada en la hoja: es lo que permite recortarla y que quede igual por los cuatro lados.
        float x0 = (hoja.getWidth() - w) / 2f;
        float y0 = (hoja.getHeight() - h) / 2f;

        Image fondo = Image.getInstance(plantillaImg);
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

        texto(lienzo, fuente, d.nombre(), valor(c.nombre(), d), x0, y0, w, h, d.centrado());
        texto(lienzo, fuente, d.empresa(), valor(empresa, d), x0, y0, w, h, d.centrado());
            texto(lienzo, fuente, d.ci(), valor(c.ci(), d), x0, y0, w, h, d.centrado());
            dibujarFoto(lienzo, d.foto(), c, x0, y0, w, h);
            if (d.zona() != null) {
            // La plantilla ya trae "COD. PUESTO" y "ZONA" impresos uno al lado del otro: cada
            // valor va en su caja y no hace falta apilarlos.
            texto(lienzo, fuente, d.codigo(), valor(c.casetas(), d), x0, y0, w, h, d.centrado());
            texto(lienzo, fuente, d.zona(), valor(c.categoria(), d), x0, y0, w, h, d.centrado());
        } else {
            // La caseta con su categoria debajo: el numero solo no dice nada si hay trece zonas.
            codigoConCategoria(lienzo, fuente, d.codigo(), valor(c.casetas(), d),
                    valor(c.categoria(), d), x0, y0, w, h, d.centrado());
        }
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

    private static String valor(String s, PlantillaCredencial d) {
        String t = s == null ? "" : s.trim();
        return d.mayusculas() ? t.toUpperCase() : t;
    }

    /**
     * Escribe un texto dentro de su caja, centrado en vertical.
     *
     * El tamaño se ENCOGE hasta que quepa. Hace falta de verdad: los nombres de entidad van de
     * "PIL" a "ASOCIACION DE PRODUCTORES AGROPECUARIOS DEL NORTE INTEGRADO", y sin esto el
     * texto largo se saldria de la credencial por el lado derecho.
     */
    private void texto(PdfContentByte lienzo, BaseFont fuente, Caja caja, String contenido,
                       float x0, float y0, float w, float h, boolean centrado) {
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
        lienzo.showTextAligned(
                centrado ? Element.ALIGN_CENTER : Element.ALIGN_LEFT,
                pintable,
                centrado ? x + cajaW / 2f : x,
                y + (cajaH - tam * 0.70f) / 2f, 0);
        lienzo.endText();
    }

    private void dibujarFoto(PdfContentByte lienzo, Caja caja, CredencialDTO c,
                             float x0, float y0, float w, float h) throws Exception {
        if (caja == null || c.fotoUrl() == null || c.fotoUrl().isBlank()) return;
        Path ruta = rutaFoto(c.fotoUrl());
        if (ruta == null) return;

        float cajaW = (float) (caja.ancho() * w);
        float cajaH = (float) (caja.alto() * h);
        float x = x0 + (float) (caja.x() * w);
        float y = y0 + h - (float) (caja.y() * h) - cajaH;

        /*
         * Una foto ilegible NO puede tumbar el lote.
         *
         * `Image.getInstance` revienta con cualquier archivo corrupto ("Premature EOF while
         * reading JPG"), y sin este catch esa excepcion sube hasta el controlador y convierte
         * en un 500 la peticion ENTERA: nadie imprime. En la feria, una foto a medias es lo
         * normal —se sube desde un telefono con mala señal— y el resultado seria que un
         * archivo roto deja sin credencial a las otras 799 personas de la tanda.
         *
         * Se salta esa foto y se sigue: una credencial sin cara es un problema de UNA persona,
         * que ademas se ve al recogerla. Queda el aviso en el registro para poder rehacerla.
         */
        try {
            Image foto = Image.getInstance(ruta.toAbsolutePath().toString());
            foto.scaleAbsolute(cajaW, cajaH);
            foto.setAbsolutePosition(x, y);
            lienzo.addImage(foto);
        } catch (Exception e) {
            log.warn("Foto ilegible en la credencial de {} ({}): {}. Se imprime sin ella.",
                    c.nombre(), c.fotoUrl(), e.getMessage());
        }
    }

    private Path rutaFoto(String fotoUrl) {
        String relativa = fotoUrl.startsWith("/files/") ? fotoUrl.substring("/files/".length()) : fotoUrl;
        try {
            Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
            Path destino = base.resolve(relativa).normalize();
            if (!destino.startsWith(base) || !Files.isRegularFile(destino)) return null;
            return destino;
        } catch (RuntimeException e) {
            log.warn("No se pudo resolver la foto {}: {}", fotoUrl, e.getMessage());
            return null;
        }
    }

    private byte[] bytes(String recurso) {
        return cache.computeIfAbsent(recurso, r -> {
            try (InputStream in = new ClassPathResource(r).getInputStream()) {
                return in.readAllBytes();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "No se encontro la plantilla de credencial: " + r, e);
            }
        });
    }
}
