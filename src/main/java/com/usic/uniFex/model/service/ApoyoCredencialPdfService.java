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
    /** Nuevo diseño Prensa: solo nombre completo + QR. */
    private static final String PLANTILLA_PRENSA_NUEVO = "static/assets/CREDENCIAL_PRENSA_NUEVO.png";
    /** Ancho de hoja; el alto sale de la proporcion de la plantilla (10 x 13). */
    private static final float ANCHO = 10 * CM;
    /** A4 en puntos. */
    private static final float A4_ANCHO = 21 * CM;
    private static final float A4_ALTO = 29.7f * CM;
    /** Maximo 4 credenciales por hoja (2x2). */
    private static final int GRUPO_DUPLEX = 4;

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
                               double avatarRadio, double qrX, double qrY, double qrLado) {
    }

    private static final Disposicion NORMAL = new Disposicion(PLANTILLA,
            new double[]{0.105, 0.329, 0.755, 0.072},
            new double[]{0.105, 0.410, 0.755, 0.088},
            new double[]{0.105, 0.508, 0.755, 0.070},
            0.500, 0.2085, 0.134,
            0.370, 0.620, 0.260);

    private static final Disposicion FUL = new Disposicion(PLANTILLA_FUL,
            new double[]{0.105, 0.336, 0.760, 0.094},
            new double[]{0.105, 0.437, 0.760, 0.092},
            new double[]{0.105, 0.533, 0.760, 0.061},
            0.502, 0.2135, 0.134,
            0.370, 0.620, 0.260);

    private static final Disposicion PRENSA = new Disposicion(PLANTILLA_PRENSA,
            new double[]{0.105, 0.336, 0.760, 0.090},
            new double[]{0.105, 0.435, 0.760, 0.083},
            new double[]{0.105, 0.527, 0.760, 0.065},
            0.500, 0.2130, 0.134,
            0.370, 0.620, 0.260);

    /** Solo nombre + QR (sin C.I., sin dependencia, sin avatar). */
    private static final Disposicion PRENSA_NUEVO = new Disposicion(PLANTILLA_PRENSA_NUEVO,
            new double[]{196.0 / 1102, 728.0 / 1427, 817.0 / 1102, 112.0 / 1427},  // nombre
            null,  // sin dependencia
            null,  // sin C.I.
            0, 0, 0,  // sin avatar
            414.0 / 1102, 861.0 / 1427, 274.0 / 1102);  // QR

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
        if (DEPENDENCIA_PRENSA.equalsIgnoreCase(dep)) return PRENSA_NUEVO;
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
        if (d.dependencia() != null) {
            textoEnCaja(lienzo, fuente, d.dependencia(), mayus(dependenciaDe(c)), w, h,
                    MARGEN_IZQ_PRIMERAS);
        }
        if (d.ci() != null) {
            textoEnCaja(lienzo, fuente, d.ci(), "C.I. " + orVacio(c.ci()), w, h, 0);
        }

        if (d.avatarRadio() > 0) {
            dibujarFotoCircular(lienzo, c, d, w, h);
        }

        // QR dentro del recuadro. El eje Y del PDF crece hacia ARRIBA y el de la
        // plantilla hacia abajo, asi que se invierte una sola vez aqui.
        float lado = (float) (d.qrLado() * w);
        float qrX = (float) (d.qrX() * w);
        float qrY = h - (float) (d.qrY() * h) - lado;
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

    /**
     * Genera PDF duplex 2x2 (4 credenciales por hoja A4, con reversos espejados).
     *
     * Cada hoja A4 rinde 4 credenciales completas (frente + reverso) al imprimir a doble
     * cara por borde largo. Los reversos se espejan en X para que al voltear la hoja
     * queden detras de su frente.
     *
     * @param credenciales lista de credenciales a imprimir
     * @param urlBase      raiz publica para el QR (ej. https://fexpo.uap.edu.bo)
     * @param codigos      servicio para generar la URL del QR
     */
    public byte[] generarDuplex4(List<ApoyoCredencialDTO> credenciales, String urlBase,
                                 ApoyoCodigoService codigos) {
        if (credenciales == null || credenciales.isEmpty()) {
            throw new IllegalArgumentException("No hay credenciales que imprimir");
        }
        try {
            // Cargar plantillas una vez
            Image reversoImg = Image.getInstance(bytes(REVERSO));
            Rectangle hoja = new Rectangle(A4_ANCHO, A4_ALTO);
            // Medida de una credencial individual (10x13 cm)
            float w = ANCHO;
            float h = ANCHO * 13f / 10f;  // proporcion 10:13
            validarCuadricula(hoja, w, h);

            // Posicion base de la cuadricula 2x2 centrada
            float x0 = (hoja.getWidth() - 2 * w) / 2;
            float y0 = (hoja.getHeight() - 2 * h) / 2;

            Document doc = new Document(hoja, 0, 0, 0, 0);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            preferenciaTamanoReal(writer);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(
                    BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

            // Plantillas reutilizables del reverso (igual para todas)
            com.itextpdf.text.pdf.PdfTemplate tplReverso = plantillaReverso(lienzo, reversoImg, w, h);

            int totalGrupos = (credenciales.size() + GRUPO_DUPLEX - 1) / GRUPO_DUPLEX;
            for (int g = 0; g < totalGrupos; g++) {
                List<ApoyoCredencialDTO> grupo = credenciales.subList(
                        g * GRUPO_DUPLEX, Math.min(credenciales.size(), (g + 1) * GRUPO_DUPLEX));

                if (g > 0) doc.newPage();  // nueva hoja para el siguiente grupo

                // ---- PAGINA DE FRENTES ----
                // Crear plantilla frente para cada credencial del grupo (pueden tener distinto fondo)
                com.itextpdf.text.pdf.PdfTemplate[] tplsFrente = new com.itextpdf.text.pdf.PdfTemplate[grupo.size()];
                for (int i = 0; i < grupo.size(); i++) {
                    Disposicion d = disposicionDe(grupo.get(i));
                    Image frenteImg = Image.getInstance(bytes(d.fondo()));
                    tplsFrente[i] = plantillaFrente(lienzo, frenteImg, w, h);
                }

                pintarGrupoFrentes(lienzo, fuente, tplsFrente, grupo, x0, y0, w, h, urlBase, codigos);
                doc.newPage();

                // ---- PAGINA DE REVERSOS (espejados en X) ----
                pintarGrupoReversos(lienzo, tplReverso, grupo.size(), x0, y0, w, h);
            }
            doc.close();
            log.info("Credenciales de apoyo duplex 2x2: {}, hojas: {}", credenciales.size(), 2 * totalGrupos);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF duplex de apoyo: "
                    + e.getMessage(), e);
        }
    }

    private void preferenciaTamanoReal(PdfWriter writer) {
        writer.addViewerPreference(com.itextpdf.text.pdf.PdfName.PRINTSCALING,
                com.itextpdf.text.pdf.PdfName.NONE);
        writer.addViewerPreference(com.itextpdf.text.pdf.PdfName.DUPLEX,
                com.itextpdf.text.pdf.PdfName.DUPLEXFLIPLONGEDGE);
    }

    private void validarCuadricula(Rectangle hoja, float w, float h) {
        if (2 * w > hoja.getWidth() || 2 * h > hoja.getHeight()) {
            throw new IllegalArgumentException("Cuatro credenciales de ese tamano no caben en la hoja A4");
        }
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

    private com.itextpdf.text.pdf.PdfTemplate plantillaReverso(
            PdfContentByte lienzo, Image reverso, float w, float h) throws Exception {
        // Cover: escala para cubrir el rectangulo w x h y recorta lo que sobra, centrado
        float escala = Math.max(w / reverso.getWidth(), h / reverso.getHeight());
        float ancho = reverso.getWidth() * escala;
        float alto = reverso.getHeight() * escala;
        Image fondo = Image.getInstance(reverso);
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

    /**
     * Pinta los frentes de un grupo (hasta 4) en la cuadricula 2x2.
     */
    private void pintarGrupoFrentes(PdfContentByte lienzo, BaseFont fuente,
                                    com.itextpdf.text.pdf.PdfTemplate[] tplsFrente,
                                    List<ApoyoCredencialDTO> grupo,
                                    float x0, float y0, float w, float h,
                                    String urlBase, ApoyoCodigoService codigos) throws Exception {
        for (int i = 0; i < grupo.size(); i++) {
            int col = i % 2;
            boolean arriba = i < 2;
            float x = x0 + col * w;
            float y = arriba ? y0 + h : y0;

            // Plantilla del fondo
            lienzo.addTemplate(tplsFrente[i], x, y);

            // Contenido (nombre, QR, etc.) sobre el fondo
            pintarContenidoEnHueco(lienzo, fuente, grupo.get(i), x, y, w, h, urlBase, codigos);

            marcasDeCorte(lienzo, x, y, w, h);
        }
        // Lineas de corte centrales
        lineaDeCorte(lienzo, x0 + w, y0, y0 + 2 * h, false); // vertical
        lineaDeCorte(lienzo, y0 + h, x0, x0 + 2 * w, true); // horizontal
    }

    /**
     * Pinta los reversos de un grupo (hasta 4) en la cuadricula 2x2 ESPEJADA EN X.
     * Columna 0 <-> 1 para que al voltear por borde largo queden detras de su frente.
     */
    private void pintarGrupoReversos(PdfContentByte lienzo,
                                     com.itextpdf.text.pdf.PdfTemplate tplReverso,
                                     int cantidad, float x0, float y0, float w, float h) throws Exception {
        for (int i = 0; i < cantidad; i++) {
            // ESPEJADO EN X: columna contraria
            int col = 1 - (i % 2);
            boolean arriba = i < 2;
            float x = x0 + col * w;
            float y = arriba ? y0 + h : y0;
            lienzo.addTemplate(tplReverso, x, y);
        }
    }

    /**
     * Pinta el contenido (nombre, QR) de una credencial dentro de su hueco.
     * Reutiliza la logica de disposicionDe pero con coordenadas relativas al hueco.
     */
    private void pintarContenidoEnHueco(PdfContentByte lienzo, BaseFont fuente,
                                        ApoyoCredencialDTO c, float x0, float y0,
                                        float w, float h, String urlBase, ApoyoCodigoService codigos)
            throws Exception {
        Disposicion d = disposicionDe(c);

        // Nombre
        textoEnCaja(lienzo, fuente, d.nombre(), mayus(c.nombreCompleto()), w, h,
                MARGEN_IZQ_PRIMERAS, x0, y0);

        if (d.dependencia() != null) {
            textoEnCaja(lienzo, fuente, d.dependencia(), mayus(dependenciaDe(c)), w, h,
                    MARGEN_IZQ_PRIMERAS, x0, y0);
        }
        if (d.ci() != null) {
            textoEnCaja(lienzo, fuente, d.ci(), "C.I. " + orVacio(c.ci()), w, h, 0, x0, y0);
        }

        if (d.avatarRadio() > 0) {
            dibujarFotoCircular(lienzo, c, d, w, h, x0, y0);
        }

        // QR
        float lado = (float) (d.qrLado() * w);
        float qrX = x0 + (float) (d.qrX() * w);
        float qrY = y0 + h - (float) (d.qrY() * h) - lado;
        BarcodeQRCode qr = new BarcodeQRCode(
                codigos.urlPublica(urlBase, c.codigo()), 1000, 1000, null);
        Image imgQr = qr.getImage();
        imgQr.scaleAbsolute(lado, lado);
        imgQr.setAbsolutePosition(qrX, qrY);
        lienzo.addImage(imgQr);
    }

    /**
     * Sobrecarga de textoEnCaja con offset de hueco (x0, y0).
     */
    private void textoEnCaja(PdfContentByte lienzo, BaseFont fuente, double[] caja,
                             String contenido, float w, float h, double margenIzq,
                             float x0, float y0) {
        if (contenido == null || contenido.isBlank()) return;
        float aire = (float) (margenIzq * w);
        float cajaW = (float) (caja[2] * w) - aire;
        float cajaH = (float) (caja[3] * h);
        float x = x0 + (float) (caja[0] * w) + aire;
        float y = y0 + h - (float) (caja[1] * h) - cajaH;

        java.util.List<String> lineas = new java.util.ArrayList<>(java.util.List.of(contenido));
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

    /**
     * Sobrecarga de dibujarFotoCircular con offset de hueco.
     */
    private void dibujarFotoCircular(PdfContentByte lienzo, ApoyoCredencialDTO c,
                                     Disposicion d, float w, float h, float x0, float y0) {
        if (c.fotoUrl() == null || c.fotoUrl().isBlank()) return;
        Path ruta = rutaFoto(c.fotoUrl());
        if (ruta == null) return;
        try {
            Image foto = Image.getInstance(ruta.toAbsolutePath().toString());
            float cx = x0 + (float) (d.avatarCx() * w);
            float cy = y0 + h - (float) (d.avatarCy() * h);
            float radio = (float) (d.avatarRadio() * w);

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

    private void marcasDeCorte(PdfContentByte lienzo, float x, float y, float w, float h) {
        float m = 8f;
        lienzo.setLineWidth(0.5f);
        lienzo.setColorStroke(BaseColor.LIGHT_GRAY);
        lienzo.moveTo(x, y + m); lienzo.lineTo(x, y);
        lienzo.moveTo(x + m, y); lienzo.lineTo(x, y);
        lienzo.moveTo(x + w - m, y); lienzo.lineTo(x + w, y);
        lienzo.moveTo(x + w, y + m); lienzo.lineTo(x + w, y);
        lienzo.moveTo(x + w, y + h - m); lienzo.lineTo(x + w, y + h);
        lienzo.moveTo(x + w - m, y + h); lienzo.lineTo(x + w, y + h);
        lienzo.moveTo(x + m, y + h); lienzo.lineTo(x, y + h);
        lienzo.moveTo(x, y + h - m); lienzo.lineTo(x, y + h);
        lienzo.stroke();
    }

    private void lineaDeCorte(PdfContentByte lienzo, float coord, float ini, float fin, boolean horizontal) {
        lienzo.setLineWidth(0.3f);
        lienzo.setColorStroke(BaseColor.LIGHT_GRAY);
        lienzo.setLineDash(4f, 4f);
        if (horizontal) {
            lienzo.moveTo(ini, coord);
            lienzo.lineTo(fin, coord);
        } else {
            lienzo.moveTo(coord, ini);
            lienzo.lineTo(coord, fin);
        }
        lienzo.stroke();
        lienzo.setLineDash(0f);
    }
}
