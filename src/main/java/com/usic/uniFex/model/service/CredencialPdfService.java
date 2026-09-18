package com.usic.uniFex.model.service;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

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
    public static final float A4_ANCHO_CM = 21f;
    public static final float A4_ALTO_CM = 29.7f;
    /** Medida impresa de la credencial EXPOSITOR: ocupa la hoja al maximo. */
    public static final float CRED_ANCHO_CM = 10f;
    public static final float CRED_ALTO_CM = 13f;

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

        pintarCredencial(lienzo, fuente, plantillaImg, d, c, x0, y0, w, h, urlBase, codigos);
    }

    /** Un solo trazado para impresion individual y por pares, en medidas finales del papel. */
    private void pintarCredencial(PdfContentByte lienzo, BaseFont fuente, Image plantillaImg,
                                  PlantillaCredencial d, CredencialDTO c,
                                  float x0, float y0, float w, float h, String urlBase,
                                  CredencialCodigoService codigos) throws Exception {

        Image fondo = Image.getInstance(plantillaImg);
        fondo.scaleAbsolute(w, h);
        fondo.setAbsolutePosition(x0, y0);
        lienzo.addImage(fondo);

        pintarContenido(lienzo, fuente, d, c, x0, y0, w, h, urlBase, codigos);
    }

    /**
     * QR, textos y foto sobre un fondo ya dibujado.
     *
     * Separado del fondo a proposito: en impresion masiva el fondo es una plantilla PDF
     * reutilizable (Form XObject) que se incrusta UNA vez, y aqui solo se pinta lo que
     * cambia por persona. Sin esto, cada pagina repetia los ~3 MB de frente+reverso y una
     * tanda de 800 credenciales tumbaba el servidor con OutOfMemoryError.
     */
    private void pintarContenido(PdfContentByte lienzo, BaseFont fuente,
                                 PlantillaCredencial d, CredencialDTO c,
                                 float x0, float y0, float w, float h, String urlBase,
                                 CredencialCodigoService codigos) throws Exception {

        // ---- QR ----
        // El eje Y del PDF crece hacia ARRIBA y el de la disposicion hacia abajo (como la
        // imagen), asi que se invierte aqui una sola vez y el resto del codigo no se entera.
        float lado = (float) (d.qr().ancho() * w);
        float qrX = x0 + (float) (d.qr().x() * w);
        float qrY = y0 + h - (float) (d.qr().y() * h) - lado;
        // Fondo blanco limpio detras del QR: la caja ya trae zona de silencio medida sobre
        // la plantilla, y asi ningun artefacto del fondo se mete entre los modulos.
        lienzo.saveState();
        lienzo.setColorFill(BaseColor.WHITE);
        lienzo.rectangle(qrX, qrY, lado, lado);
        lienzo.fill();
        lienzo.restoreState();
        BarcodeQRCode qr = new BarcodeQRCode(
                codigos.urlPublica(urlBase, c.codigo()), 400, 400, null);
        Image imgQr = qr.getImage();
        imgQr.scaleAbsolute(lado, lado);
        imgQr.setAbsolutePosition(qrX, qrY);
        lienzo.addImage(imgQr);

        // ---- Datos ----
        String empresa = c.entidad() == null ? "" : c.entidad();
        if (c.rubro() != null && !c.rubro().isBlank()) empresa += "  ·  " + c.rubro();

        textoDato(lienzo, fuente, d.nombre(), valor(c.nombre(), d), x0, y0, w, h, d);
        textoDato(lienzo, fuente, d.empresa(), valor(empresa, d), x0, y0, w, h, d);
        textoDato(lienzo, fuente, d.ci(), valor(c.ci(), d), x0, y0, w, h, d);
        dibujarFoto(lienzo, d.foto(), c, x0, y0, w, h, "EXPOSITOR".equals(d.id()));
        if (d.zona() != null) {
            // La plantilla ya trae "COD. PUESTO" y "ZONA" impresos uno al lado del otro: cada
            // valor va en su caja y no hace falta apilarlos.
            textoDato(lienzo, fuente, d.codigo(), valor(c.casetas(), d), x0, y0, w, h, d);
            textoDato(lienzo, fuente, d.zona(), valor(c.categoria(), d), x0, y0, w, h, d);
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

    /** En expositor admite dos lineas antes de reducir o abreviar un valor largo. */
    private void textoDato(PdfContentByte lienzo, BaseFont fuente, Caja caja, String contenido,
                           float x0, float y0, float w, float h, PlantillaCredencial plantilla) {
        if (caja == null || contenido == null || contenido.isBlank()) return;
        if (!"EXPOSITOR".equals(plantilla.id())) {
            texto(lienzo, fuente, caja, contenido, x0, y0, w, h, plantilla.centrado());
            return;
        }
        float ancho = (float) caja.ancho() * w;
        float alto = (float) caja.alto() * h;
        String limpio = contenido.replaceAll("\\s+", " ").trim();
        for (float tam = Math.min(12f, alto * .72f); tam >= 6f; tam -= .25f) {
            var lineas = new java.util.ArrayList<String>();
            String linea = "";
            boolean palabraLarga = false;
            for (String palabra : limpio.split(" ")) {
                if (fuente.getWidthPoint(palabra, tam) > ancho) { palabraLarga = true; break; }
                String candidata = linea.isEmpty() ? palabra : linea + " " + palabra;
                if (!linea.isEmpty() && fuente.getWidthPoint(candidata, tam) > ancho) {
                    lineas.add(linea);
                    linea = palabra;
                } else linea = candidata;
            }
            if (palabraLarga) continue;
            if (!linea.isEmpty()) lineas.add(linea);
            float ascenso = fuente.getFontDescriptor(BaseFont.ASCENT, tam);
            float descenso = fuente.getFontDescriptor(BaseFont.DESCENT, tam);
            float interlineado = tam * 1.1f;
            float altoTexto = ascenso - descenso + (lineas.size() - 1) * interlineado;
            if (lineas.size() > 2 || altoTexto > alto) continue;
            float x = x0 + (float) caja.x() * w;
            float y = y0 + h - (float) caja.y() * h - alto;
            float base = y + (alto + altoTexto) / 2 - ascenso;
            lienzo.beginText();
            lienzo.setFontAndSize(fuente, tam);
            lienzo.setColorFill(BaseColor.BLACK);
            for (String parte : lineas) {
                lienzo.showTextAligned(Element.ALIGN_LEFT, parte, x, base, 0);
                base -= interlineado;
            }
            lienzo.endText();
            return;
        }
        texto(lienzo, fuente, caja, limpio, x0, y0, w, h, false);
    }

    private void dibujarFoto(PdfContentByte lienzo, Caja caja, CredencialDTO c,
                             float x0, float y0, float w, float h, boolean circular) throws Exception {
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
            Image foto = fotoReducida(ruta);
            if (circular) {
                float escala = Math.max(cajaW / foto.getWidth(), cajaH / foto.getHeight());
                foto.scaleAbsolute(foto.getWidth() * escala, foto.getHeight() * escala);
                foto.setAbsolutePosition(x + (cajaW - foto.getScaledWidth()) / 2,
                        y + (cajaH - foto.getScaledHeight()) / 2);
                lienzo.saveState();
                try {
                    lienzo.ellipse(x, y, x + cajaW, y + cajaH);
                    lienzo.clip();
                    lienzo.newPath();
                    lienzo.addImage(foto);
                } finally {
                    lienzo.restoreState();
                }
                return;
            }
            foto.scaleAbsolute(cajaW, cajaH);
            foto.setAbsolutePosition(x, y);
            lienzo.addImage(foto);
        } catch (Exception e) {
            log.warn("Foto ilegible en la credencial de {} ({}): {}. Se imprime sin ella.",
                    c.nombre(), c.fotoUrl(), e.getMessage());
        }
    }

    /**
     * La foto reducida a miniatura JPEG antes de incrustarla.
     *
     * Las fotos se suben desde telefonos y suelen traer 3000+ px cuando impresa ocupa ~3 cm
     * (~90 pt): incrustar el archivo tal cual mete MB por credencial y una tanda completa
     * agotaba la memoria (OutOfMemoryError). A 360 px sobra resolucion para 3 cm de papel
     * (~300 ppp) y cada foto pesa decenas de KB en vez de MB.
     */
    private static final int FOTO_MAX_PX = 360;

    private Image fotoReducida(Path ruta) throws Exception {
        BufferedImage original = ImageIO.read(ruta.toFile());
        if (original == null) throw new java.io.IOException("Formato de imagen no reconocido");
        int ancho = original.getWidth(), alto = original.getHeight();
        double escala = Math.min(1.0, FOTO_MAX_PX / (double) Math.max(ancho, alto));
        BufferedImage lista = original;
        if (escala < 1.0 || original.getColorModel().hasAlpha()) {
            int nuevoAncho = Math.max(1, (int) (ancho * escala));
            int nuevoAlto = Math.max(1, (int) (alto * escala));
            BufferedImage chica = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = chica.createGraphics();
            try {
                // El JPEG no admite transparencia: se aplana sobre blanco.
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, nuevoAncho, nuevoAlto);
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
            } finally {
                g.dispose();
            }
            lista = chica;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(lista, "jpg", bos);
        return Image.getInstance(bos.toByteArray());
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

    /**
     * Lotes de 4 credenciales de 10x13 cm para impresora doble cara.
     *
     * <h2>Disposicion: cuadricula 2x2 sobre A4, todo en UN solo PDF</h2>
     * Cada grupo de hasta 4 ocupa 2 paginas sobre hoja A4 (21x29,7 cm): la primera con los
     * FRENTES en cuadricula 2x2 y la segunda con sus REVERSOS fijos. El bloque mide 20x26:
     * 0,5 cm de aire a los lados y 1,85 arriba/abajo. Las medidas son reales: nunca se
     * agranda o encoge. Imprimir a doble cara volteando por el <b>borde largo</b> y al 100 %.
     * Para imprimir de a pocos, se eligen rangos de paginas (cada lote son 2 paginas seguidas).
     *
     * <h2>La credencial se imprime a 10x13 y la plantilla ya trae esa proporcion</h2>
     * La imagen de fondo (1102x1427, proporcion 1,295 ~ 10:13) se ajusta al rectangulo casi
     * sin deformar (0,4 %); como textos, QR y foto van en fracciones del mismo rectangulo,
     * todo coincide con su tarjeta. El QR sale cuadrado perfecto.
     *
     * <h2>Un solo PDF y no un ZIP con un PDF por lote, a proposito</h2>
     * Cada PDF tendria que incrustar de nuevo los ~3 MB de frente+reverso: 800 credenciales
     * en 400 PDFs pesaban mas de 1 GB (medido con lotes de 4: 567 MB). Concatenados en un
     * solo documento los fondos se incrustan UNA vez y las 800 pesan ~5 MB. El resultado
     * impreso es el mismo.
     *
     * <h2>Los reversos van espejados en X</h2>
     * Al voltear la hoja por el borde largo (eje vertical), lo impreso en (x, y) del reverso
     * cae detras de (anchoHoja - x, y) del frente. Por eso el reverso de cada credencial va
     * en la columna contraria a su frente (misma fila). Como el reverso es la misma imagen
     * fija para todos, con el lote completo la pagina queda igual —pero con lotes
     * incompletos (1 a 3) el espejado es lo que deja cada reverso detras de su frente—.
     * Ocupa el mismo rectangulo de 10x13 recortado centrado, sin deformar.
     *
     * <h2>Memoria</h2>
     * Frente y reverso se incrustan UNA vez por documento como plantillas reutilizables y las
     * fotos se reducen a miniatura: "imprimir todas" ya no agota la memoria.
     */
    public static final int GRUPO_DUPLEX = 4;

    /**
     * Un lote de hasta 4 credenciales: pagina 1 con los frentes 2x2, pagina 2 con los reversos.
     */
    public byte[] generarGrupoDe4(List<CredencialDTO> grupo, PlantillaCredencial plantilla,
                                  double anchoCm, double altoCm,
                                  String urlBase, CredencialCodigoService codigos) {
        if (grupo == null || grupo.isEmpty() || grupo.size() > GRUPO_DUPLEX) {
            throw new IllegalArgumentException("El lote debe tener entre 1 y 4 credenciales");
        }
        float[] medidas = medidasImpresas(plantilla, anchoCm, altoCm);
        Rectangle hoja = new Rectangle(A4_ANCHO_CM * CM, A4_ALTO_CM * CM);
        Document doc = new Document(hoja, 0, 0, 0, 0);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            Image frente = Image.getInstance(bytes(plantilla.imagen()));
            float w = medidas[0], h = medidas[1];
            validarCuadricula(hoja, w, h);
            // Cuadricula 2x2 centrada. Hueco 0: arriba-izq, 1: arriba-der, 2: abajo-izq, 3: abajo-der.
            float x0 = (hoja.getWidth() - 2 * w) / 2;
            float y0 = (hoja.getHeight() - 2 * h) / 2;
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            preferenciaTamanoReal(writer);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            com.itextpdf.text.pdf.PdfTemplate tplFrente = plantillaFrente(lienzo, frente, w, h);
            com.itextpdf.text.pdf.PdfTemplate tplReverso = plantillaReverso(lienzo, w, h);

            pintarGrupo(lienzo, fuente, tplFrente, tplReverso, grupo, plantilla,
                    x0, y0, w, h, urlBase, codigos, false);
            doc.newPage();
            pintarGrupo(lienzo, fuente, tplFrente, tplReverso, grupo, plantilla,
                    x0, y0, w, h, urlBase, codigos, true);
            doc.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del lote: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    /**
     * Todas las credenciales en UN solo PDF duplex: 2 paginas por cada grupo de hasta 4.
     *
     * Es el mismo formato por lotes (cuadricula 2x2, reversos espejados), pero concatenado.
     */
    public byte[] generarDuplex4(List<CredencialDTO> credenciales, PlantillaCredencial plantilla,
                                 double anchoCm, double altoCm,
                                 String urlBase, CredencialCodigoService codigos) {
        if (credenciales == null || credenciales.isEmpty()) {
            throw new IllegalArgumentException("No hay credenciales que imprimir");
        }
        float[] medidas = medidasImpresas(plantilla, anchoCm, altoCm);
        Rectangle hoja = new Rectangle(A4_ANCHO_CM * CM, A4_ALTO_CM * CM);
        Document doc = new Document(hoja, 0, 0, 0, 0);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            Image frente = Image.getInstance(bytes(plantilla.imagen()));
            float w = medidas[0], h = medidas[1];
            validarCuadricula(hoja, w, h);
            // Cuadricula 2x2 centrada. Hueco 0: arriba-izq, 1: arriba-der, 2: abajo-izq, 3: abajo-der.
            float x0 = (hoja.getWidth() - 2 * w) / 2;
            float y0 = (hoja.getHeight() - 2 * h) / 2;
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            preferenciaTamanoReal(writer);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            com.itextpdf.text.pdf.PdfTemplate tplFrente = plantillaFrente(lienzo, frente, w, h);
            com.itextpdf.text.pdf.PdfTemplate tplReverso = plantillaReverso(lienzo, w, h);

            int total = (credenciales.size() + GRUPO_DUPLEX - 1) / GRUPO_DUPLEX;
            for (int lote = 0; lote < total; lote++) {
                List<CredencialDTO> grupo = credenciales.subList(
                        lote * GRUPO_DUPLEX, Math.min(credenciales.size(), (lote + 1) * GRUPO_DUPLEX));
                if (lote > 0) doc.newPage();
                pintarGrupo(lienzo, fuente, tplFrente, tplReverso, grupo, plantilla,
                        x0, y0, w, h, urlBase, codigos, false);
                doc.newPage();
                pintarGrupo(lienzo, fuente, tplFrente, tplReverso, grupo, plantilla,
                        x0, y0, w, h, urlBase, codigos, true);
            }
            doc.close();
            log.info("Credenciales duplex 2x2: {}, hojas: {}", credenciales.size(), 2 * total);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF duplex: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    /** Medidas impresas en puntos; el alto se pide explicito para no depender de la plantilla. */
    private float[] medidasImpresas(PlantillaCredencial plantilla, double anchoCm, double altoCm) {
        if (!Double.isFinite(anchoCm) || anchoCm <= 0 || !Double.isFinite(altoCm) || altoCm <= 0) {
            throw new IllegalArgumentException("Las medidas de la credencial deben ser positivas");
        }
        if (plantilla == null || plantilla.qr() == null) {
            throw new IllegalArgumentException("La plantilla no trae disposicion del QR");
        }
        return new float[]{(float) (anchoCm * CM), (float) (altoCm * CM)};
    }

    private void validarCuadricula(Rectangle hoja, float w, float h) {
        if (2 * w > hoja.getWidth() || 2 * h > hoja.getHeight()) {
            throw new IllegalArgumentException("Cuatro credenciales de ese tamano no caben en la hoja");
        }
    }

    private void preferenciaTamanoReal(PdfWriter writer) {
        writer.addViewerPreference(com.itextpdf.text.pdf.PdfName.PRINTSCALING,
                com.itextpdf.text.pdf.PdfName.NONE);
        writer.addViewerPreference(com.itextpdf.text.pdf.PdfName.DUPLEX,
                com.itextpdf.text.pdf.PdfName.DUPLEXFLIPLONGEDGE);
    }

    private com.itextpdf.text.pdf.PdfTemplate plantillaFrente(
            PdfContentByte lienzo, Image frente, float w, float h) throws Exception {
        com.itextpdf.text.pdf.PdfTemplate tpl = lienzo.createTemplate(w, h);
        Image f = Image.getInstance(frente);
        f.scaleAbsolute(w, h);
        f.setAbsolutePosition(0, 0);
        tpl.addImage(f);
        return tpl;
    }

    /**
     * Una pagina del lote: frentes en sus huecos o reversos en los huecos espejados en X.
     *
     * Hueco i: columna i%2, fila arriba si i&lt;2. En reversos la columna es la contraria
     * (1 - i%2) para que el duplex por borde largo deje cada reverso detras de su frente.
     */
    private void pintarGrupo(PdfContentByte lienzo, BaseFont fuente,
                             com.itextpdf.text.pdf.PdfTemplate tplFrente,
                             com.itextpdf.text.pdf.PdfTemplate tplReverso,
                             List<CredencialDTO> grupo, PlantillaCredencial plantilla,
                             float x0, float y0, float w, float h,
                             String urlBase, CredencialCodigoService codigos,
                             boolean reversos) throws Exception {
        boolean[] columna = new boolean[2];
        boolean[] fila = new boolean[2];
        for (int i = 0; i < grupo.size(); i++) {
            int col = reversos ? 1 - (i % 2) : i % 2;
            boolean arriba = i < 2;
            float x = x0 + col * w, y = arriba ? y0 + h : y0;
            if (reversos) {
                lienzo.addTemplate(tplReverso, x, y);
            } else {
                lienzo.addTemplate(tplFrente, x, y);
                pintarContenido(lienzo, fuente, plantilla, grupo.get(i),
                        x, y, w, h, urlBase, codigos);
            }
            marcasDeCorte(lienzo, x, y, w, h);
            columna[col] = true;
            fila[arriba ? 1 : 0] = true;
        }
        if (columna[0] && columna[1]) lineaDeCorte(lienzo, x0 + w, y0, y0 + 2 * h);
        if (fila[0] && fila[1]) lineaDeCorte(lienzo, y0 + h, x0, x0 + 2 * w, true);
    }

    /**
     * El reverso fijo como plantilla reutilizable de 10x15, derecho (sin girar).
     *
     * En duplex por borde largo la hoja voltea sobre el eje vertical: la Y se mantiene y la
     * X se espeja sola al voltear, asi el reverso se imprime derecho y solo cambia de columna.
     * El reverso (1183x1535) es mas ancho que el frente (1024x1536): encajarlo entero
     * dejaria franjas blancas y el corte del frente no serviria para el dorso. Se escala
     * en modo cover —lo justo para cubrir los 10x15— y se recorta lo que sobre por los
     * lados, centrando el logo y el texto.
     */
    private com.itextpdf.text.pdf.PdfTemplate plantillaReverso(
            PdfContentByte lienzo, float w, float h) throws Exception {
        Image base = Image.getInstance(bytes("static/assets/CREDENCIAL_REVERSO.png"));
        float escala = Math.max(w / base.getWidth(), h / base.getHeight());
        float ancho = base.getWidth() * escala;
        float alto = base.getHeight() * escala;
        Image fondo = Image.getInstance(base);
        fondo.scaleAbsolute(ancho, alto);
        fondo.setAbsolutePosition((w - ancho) / 2f, (h - alto) / 2f);
        com.itextpdf.text.pdf.PdfTemplate tpl = lienzo.createTemplate(w, h);
        tpl.saveState();
        try {
            tpl.rectangle(0, 0, w, h);
            tpl.clip();
            tpl.newPath();
            tpl.addImage(fondo);
        } finally {
            tpl.restoreState();
        }
        return tpl;
    }

    /** Linea de corte entre huecos: un tajo vertical (u horizontal) los separa. */
    private void lineaDeCorte(PdfContentByte lienzo, float x, float y0, float y1) {
        lineaDeCorte(lienzo, x, y0, y1, false);
    }

    private void lineaDeCorte(PdfContentByte lienzo, float pos, float desde, float hasta,
                              boolean horizontal) {
        lienzo.saveState();
        lienzo.setColorStroke(new BaseColor(150, 150, 150));
        lienzo.setLineWidth(.5f);
        if (horizontal) {
            lienzo.moveTo(desde, pos);
            lienzo.lineTo(hasta, pos);
        } else {
            lienzo.moveTo(pos, desde);
            lienzo.lineTo(pos, hasta);
        }
        lienzo.stroke();
        lienzo.restoreState();
    }

    private void marcasDeCorte(PdfContentByte lienzo, float x, float y, float w, float h) {
        float separacion = .06f * CM;
        float largo = .18f * CM;
        lienzo.saveState();
        lienzo.setColorStroke(new BaseColor(150, 150, 150));
        lienzo.setLineWidth(.3f);
        for (float bordeX : new float[]{x, x + w}) {
            float direccion = bordeX == x ? -1 : 1;
            for (float bordeY : new float[]{y, y + h}) {
                lienzo.moveTo(bordeX + direccion * separacion, bordeY);
                lienzo.lineTo(bordeX + direccion * (separacion + largo), bordeY);
                float vertical = bordeY == y ? -1 : 1;
                lienzo.moveTo(bordeX, bordeY + vertical * separacion);
                lienzo.lineTo(bordeX, bordeY + vertical * (separacion + largo));
            }
        }
        lienzo.stroke();
        lienzo.restoreState();
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
