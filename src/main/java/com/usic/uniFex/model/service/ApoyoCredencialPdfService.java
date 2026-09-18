package com.usic.uniFex.model.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.uniFex.model.dto.ApoyoCredencialDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Credencial del personal de apoyo sobre su plantilla, una por hoja.
 *
 * <h2>La hoja es de 10 x 13 cm, no de 10 x 15</h2>
 * La plantilla ({@code static/assets/CREDENCIAL_APOYO.png}, 1100x1429) tiene proporcion
 * 10:13. Forzarla a 10x15 la estiraria un 15 % y un QR estirado no se lee en la puerta,
 * asi que la hoja copia la proporcion de la imagen: 10 cm de ancho por 13 de alto. Si
 * algun dia llega la plantilla en 2:3 de verdad, el unico cambio es esta medida.
 *
 * <h2>Frente y reverso</h2>
 * Cada credencial ocupa DOS paginas: el frente con sus datos y el reverso
 * ({@code static/assets/CREDENCIAL_REVERSO.png}) tal cual, para impresion duplex o para
 * recortar y pegar las dos caras.
 *
 * <h2>Solo cuatro cosas</h2>
 * Nombre, C.I., dependencia y QR, cada uno en su caja de la plantilla (fracciones 0..1
 * medidas sobre la imagen real). Nada de rol, tarea ni franjas dibujadas: ya vienen
 * impresos en el fondo. El QR lleva el codigo FXA, que el control de puerta ya sabe
 * leer (ver AccesoApiController).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApoyoCredencialPdfService {

    private static final float CM = 72f / 2.54f;
    private static final String PLANTILLA = "static/assets/CREDENCIAL_APOYO.png";
    /** El reverso ya trae todo impreso (terminos y logos): se dibuja tal cual. */
    private static final String REVERSO = "static/assets/CREDENCIAL_REVERSO.png";
    /** Fondo naranja para la Federacion Universitaria Local (misma diagramacion). */
    private static final String PLANTILLA_FUL = "static/assets/CREDENCIAL_FUL.png";
    /** Fondo amarillo para Prensa (misma diagramacion alta que la FUL). */
    private static final String PLANTILLA_PRENSA = "static/assets/CREDENCIAL_PRENSA.png";
    /** Ancho de hoja; el alto sale de la proporcion de la plantilla (10 x 13). */
    private static final float ANCHO = 10 * CM;

    /** La dependencia que sale con fondo naranja. Se compara sin importar mayusculas. */
    private static final String DEPENDENCIA_FUL = "Federación Universitaria Local";
    /** La dependencia que sale con fondo amarillo. Se compara sin importar mayusculas. */
    private static final String DEPENDENCIA_PRENSA = "Prensa";

    /**
     * Donde va cada cosa en cada fondo, en fracciones medidas sobre su PNG. El QR, el
     * reverso y el margen izquierdo son iguales en los dos; las barras de la FUL son mas
     * altas y su avatar va unos pixeles mas abajo, asi que cada fondo trae sus cajas.
     */
    private record Disposicion(String fondo, double[] nombre, double[] dependencia,
                               double[] ci, double avatarCx, double avatarCy,
                               double avatarRadio) {
    }

    private static final Disposicion NORMAL = new Disposicion(PLANTILLA,
            new double[]{0.105, 0.329, 0.755, 0.072},
            new double[]{0.105, 0.410, 0.755, 0.088},
            new double[]{0.105, 0.508, 0.755, 0.070},
            0.500, 0.2085, 0.134);

    private static final Disposicion FUL = new Disposicion(PLANTILLA_FUL,
            new double[]{0.105, 0.336, 0.760, 0.094},
            new double[]{0.105, 0.437, 0.760, 0.092},
            new double[]{0.105, 0.533, 0.760, 0.061},
            0.502, 0.2135, 0.134);

    private static final Disposicion PRENSA = new Disposicion(PLANTILLA_PRENSA,
            new double[]{0.105, 0.336, 0.760, 0.090},
            new double[]{0.105, 0.435, 0.760, 0.083},
            new double[]{0.105, 0.527, 0.760, 0.065},
            0.500, 0.2130, 0.134);

    /** Cuadrado interior del recuadro del QR (igual en los dos fondos). */
    private static final double QR_X = 0.370;
    private static final double QR_Y = 0.620;
    private static final double QR_LADO_ANCHO = 0.260;

    /**
     * Aire extra a la izquierda dentro de las dos primeras barras (fraccion del ancho).
     * El texto se centra en lo que queda: se despega un poco del filo izquierdo.
     */
    private static final double MARGEN_IZQ_PRIMERAS = 0.025;

    private static final BaseColor TINTA = new BaseColor(25, 25, 25);

    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    /** Donde viven las fotos subidas. Es la misma raiz que sirve /files/**. */
    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    /**
     * @param credenciales fichas a imprimir, una por hoja
     * @param urlBase      raiz publica para el QR (ej. https://fexpo.uap.edu.bo)
     */
    public byte[] generar(List<ApoyoCredencialDTO> credenciales, String urlBase,
                          ApoyoCodigoService codigos) {
        if (credenciales == null || credenciales.isEmpty()) {
            throw new IllegalArgumentException("No hay credenciales que imprimir");
        }
        try {
            Image fondoReverso = Image.getInstance(bytes(REVERSO));
            // Los dos fondos tienen la misma proporcion: una sola medida de hoja.
            Image muestra = Image.getInstance(bytes(PLANTILLA));
            float alto = ANCHO * muestra.getHeight() / muestra.getWidth();
            Document doc = new Document(new Rectangle(ANCHO, alto), 0, 0, 0, 0);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(
                    BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            for (int i = 0; i < credenciales.size(); i++) {
                if (i > 0) doc.newPage();
                pintar(lienzo, fuente, credenciales.get(i), urlBase, codigos);
                // El reverso: segunda pagina de la misma credencial, para imprimir
                // frente y reverso (duplex o dos caras a pegar).
                doc.newPage();
                pintarReverso(lienzo, fondoReverso);
            }
            doc.close();
            log.info("Credenciales de apoyo impresas: {}", credenciales.size());
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de apoyo: "
                    + e.getMessage(), e);
        }
    }

    /** La disposicion segun la dependencia: FUL en naranja, Prensa en amarillo. */
    static Disposicion disposicionDe(ApoyoCredencialDTO c) {
        String dep = c.dependenciaNombre() == null ? "" : c.dependenciaNombre().trim();
        if (DEPENDENCIA_FUL.equalsIgnoreCase(dep)) return FUL;
        if (DEPENDENCIA_PRENSA.equalsIgnoreCase(dep)) return PRENSA;
        return NORMAL;
    }

    private void pintar(PdfContentByte lienzo, BaseFont fuente,
                        ApoyoCredencialDTO c, String urlBase, ApoyoCodigoService codigos)
            throws Exception {
        Disposicion d = disposicionDe(c);
        Image fondoOriginal = Image.getInstance(bytes(d.fondo()));
        float w = ANCHO;
        float h = w * fondoOriginal.getHeight() / fondoOriginal.getWidth();

        Image fondo = Image.getInstance(fondoOriginal);
        fondo.scaleAbsolute(w, h);
        fondo.setAbsolutePosition(0, 0);
        lienzo.addImage(fondo);

        textoEnCaja(lienzo, fuente, d.nombre(), mayus(c.nombreCompleto()), w, h,
                MARGEN_IZQ_PRIMERAS);
        textoEnCaja(lienzo, fuente, d.dependencia(), mayus(dependenciaDe(c)), w, h,
                MARGEN_IZQ_PRIMERAS);
        textoEnCaja(lienzo, fuente, d.ci(), "C.I. " + orVacio(c.ci()), w, h, 0);

        dibujarFotoCircular(lienzo, c, d, w, h);

        // QR dentro del recuadro rojo. El eje Y del PDF crece hacia ARRIBA y el de la
        // plantilla hacia abajo, asi que se invierte una sola vez aqui.
        float lado = (float) (QR_LADO_ANCHO * w);
        float qrX = (float) (QR_X * w);
        float qrY = h - (float) (QR_Y * h) - lado;
        BarcodeQRCode qr = new BarcodeQRCode(
                codigos.urlPublica(urlBase, c.codigo()), 1000, 1000, null);
        Image imgQr = qr.getImage();
        imgQr.scaleAbsolute(lado, lado);
        imgQr.setAbsolutePosition(qrX, qrY);
        lienzo.addImage(imgQr);
    }

    /** El reverso ocupa toda su pagina: ya viene diseñado, sin datos encima. */
    private void pintarReverso(PdfContentByte lienzo, Image fondoReverso) throws Exception {
        Image fondo = Image.getInstance(fondoReverso);
        fondo.scaleAbsolute(ANCHO, ANCHO * fondo.getHeight() / fondo.getWidth());
        fondo.setAbsolutePosition(0, 0);
        lienzo.addImage(fondo);
    }

    /**
     * La foto en el circulo del avatar, recortada en circulo (cover: llena sin deformar).
     *
     * Sin foto o con archivo ilegible no se dibuja nada y queda el avatar generico de la
     * plantilla: una cara menos es un problema de UNA credencial, no de todo el lote.
     */
    private void dibujarFotoCircular(PdfContentByte lienzo, ApoyoCredencialDTO c,
                                     Disposicion d, float w, float h) {
        if (c.fotoUrl() == null || c.fotoUrl().isBlank()) return;
        Path ruta = rutaFoto(c.fotoUrl());
        if (ruta == null) return;
        try {
            Image foto = Image.getInstance(ruta.toAbsolutePath().toString());
            float cx = (float) (d.avatarCx() * w);
            float cy = h - (float) (d.avatarCy() * h);
            float radio = (float) (d.avatarRadio() * w);

            // Cover: se escala para tapar el circulo y se centra; lo que sobra lo corta el clip.
            float s = Math.max(2 * radio / foto.getWidth(), 2 * radio / foto.getHeight());
            float fw = foto.getWidth() * s;
            float fh = foto.getHeight() * s;
            foto.scaleAbsolute(fw, fh);
            foto.setAbsolutePosition(cx - fw / 2f, cy - fh / 2f);

            lienzo.saveState();
            lienzo.circle(cx, cy, radio);
            lienzo.clip();
            lienzo.newPath();
            lienzo.addImage(foto);
            lienzo.restoreState();
        } catch (Exception e) {
            log.warn("Foto ilegible en la credencial de apoyo {} ({}): {}. Sale sin ella.",
                    c.nombreCompleto(), c.fotoUrl(), e.getMessage());
        }
    }

    private Path rutaFoto(String fotoUrl) {
        String relativa = fotoUrl.startsWith("/files/")
                ? fotoUrl.substring("/files/".length()) : fotoUrl;
        try {
            Path base = java.nio.file.Paths.get(uploadRoot).toAbsolutePath().normalize();
            Path destino = base.resolve(relativa).normalize();
            if (!destino.startsWith(base) || !java.nio.file.Files.isRegularFile(destino)) return null;
            return destino;
        } catch (RuntimeException e) {
            log.warn("No se pudo resolver la foto {}: {}", fotoUrl, e.getMessage());
            return null;
        }
    }

    private static String dependenciaDe(ApoyoCredencialDTO c) {
        return c.dependenciaNombre() == null ? "" : c.dependenciaNombre().trim();
    }

    /**
     * Texto centrado en su caja, sin salirse nunca de ella.
     *
     * Primero intenta en UNA linea encogiendose; si ni al minimo cabe, se parte en DOS
     * lineas por un espacio y cada una se encoge por su cuenta; si ni asi entra, se recorta
     * con puntos suspensivos. Dos es el maximo: la caja no da para mas renglones legibles.
     */
    private void textoEnCaja(PdfContentByte lienzo, BaseFont fuente, double[] caja,
                             String contenido, float w, float h, double margenIzq) {
        if (contenido == null || contenido.isBlank()) return;
        float aire = (float) (margenIzq * w);
        float cajaW = (float) (caja[2] * w) - aire;
        float cajaH = (float) (caja[3] * h);
        float x = (float) (caja[0] * w) + aire;
        float y = h - (float) (caja[1] * h) - cajaH;

        java.util.List<String> lineas = new java.util.ArrayList<>(java.util.List.of(contenido));
        // Se mide contra el 96 % de la caja: la metrica de la fuente es teorica y el borde
        // redondeado muerde los extremos, asi que sin ese colchon el texto largo besa el filo.
        float anchoUtil = cajaW * 0.96f;
        float tam = ajustar(fuente, contenido, anchoUtil, cajaH * 0.62f, cajaH * 0.28f);
        if (fuente.getWidthPoint(contenido, tam) > anchoUtil) {
            lineas = partirEnDos(contenido);
            tam = Float.MAX_VALUE;
            for (String l : lineas) {
                tam = Math.min(tam, ajustar(fuente, l, anchoUtil, cajaH * 0.31f, cajaH * 0.14f));
            }
            for (int i = 0; i < lineas.size(); i++) {
                String l = lineas.get(i);
                if (fuente.getWidthPoint(l, tam) > anchoUtil) {
                    lineas.set(i, recortar(l, anchoUtil, fuente, tam));
                }
            }
        }

        // El bloque (una o dos lineas) queda centrado en vertical dentro de la caja.
        float altoLinea = tam * 1.15f;
        float yTexto = y + (cajaH + (lineas.size() - 1) * altoLinea - tam * 0.70f) / 2f;
        for (int i = lineas.size() - 1; i >= 0; i--) {
            lienzo.beginText();
            lienzo.setFontAndSize(fuente, tam);
            lienzo.setColorFill(TINTA);
            lienzo.showTextAligned(Element.ALIGN_CENTER, lineas.get(i),
                    x + cajaW / 2f, yTexto, 0);
            lienzo.endText();
            yTexto -= altoLinea;
        }
    }

    /** El mayor tamaño entre maximo y minimo con el que el texto cabe en el ancho. */
    private static float ajustar(BaseFont fuente, String texto, float ancho,
                                 float maximo, float minimo) {
        float t = maximo;
        while (t > minimo && fuente.getWidthPoint(texto, t) > ancho) t -= 0.5f;
        return t;
    }

    /**
     * Parte por un espacio buscando que la linea mas larga sea lo mas corta posible. Si no
     * hay por donde partir (una sola palabra), se devuelve entera y el recorte la remata.
     */
    private static java.util.List<String> partirEnDos(String s) {
        String[] palabras = s.trim().split("\\s+");
        if (palabras.length < 2) return new java.util.ArrayList<>(java.util.List.of(s));
        int mejor = 1;
        int mejorMax = Integer.MAX_VALUE;
        for (int i = 1; i < palabras.length; i++) {
            String a = String.join(" ", java.util.Arrays.copyOfRange(palabras, 0, i));
            String b = String.join(" ", java.util.Arrays.copyOfRange(palabras, i, palabras.length));
            int m = Math.max(a.length(), b.length());
            if (m < mejorMax) {
                mejorMax = m;
                mejor = i;
            }
        }
        var lineas = new java.util.ArrayList<String>();
        lineas.add(String.join(" ", java.util.Arrays.copyOfRange(palabras, 0, mejor)));
        lineas.add(String.join(" ", java.util.Arrays.copyOfRange(palabras, mejor, palabras.length)));
        return lineas;
    }

    private static String recortar(String s, float cajaW, BaseFont fuente, float tam) {
        String p = s;
        while (p.length() > 4 && fuente.getWidthPoint(p + "…", tam) > cajaW) {
            p = p.substring(0, p.length() - 1);
        }
        return p + "…";
    }

    private static String mayus(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private static String orVacio(String s) {
        return s == null ? "" : s.trim();
    }

    private byte[] bytes(String recurso) {
        return cache.computeIfAbsent(recurso, r -> {
            try (InputStream in = new org.springframework.core.io.ClassPathResource(r)
                    .getInputStream()) {
                return in.readAllBytes();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "No se encontro la plantilla de apoyo: " + r, e);
            }
        });
    }
}
