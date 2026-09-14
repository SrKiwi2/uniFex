package com.usic.uniFex.model.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.itextpdf.text.pdf.BarcodeQRCode;
import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.dto.PlantillaCredencial.Caja;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * La credencial virtual, como IMAGEN.
 *
 * <h2>Por que una imagen y no un PDF</h2>
 * Esta credencial no se imprime: se entrega al telefono del expositor y se enseña en la puerta
 * desde la pantalla. Un PDF pensado para hoja carta en un movil sale diminuto y hay que
 * ampliarlo con dos dedos justo cuando hay cola. Una imagen vertical se abre en la galeria, se
 * manda por WhatsApp y se ve entera de un vistazo.
 *
 * Es un PNG y no un JPEG aunque la plantilla lo sea: el QR tiene bordes duros y la compresion
 * con perdida los emborrona, que es exactamente lo que un lector necesita nitido.
 *
 * <h2>Que dibuja</h2>
 * La plantilla de fondo, el QR con la URL publica, la foto del responsable recortada en
 * circulo, y los datos en las cajas que declara {@link PlantillaCredencial}. Las posiciones
 * salen de ahi, no de aqui: este servicio solo pinta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredencialImagenService {

    private final CredencialCodigoService codigos;

    /** Donde viven las fotos subidas. Es la misma raiz que sirve /files/**. */
    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    /**
     * El color del texto. Gris muy oscuro y no negro puro: sobre el blanco de las cajas el
     * negro absoluto vibra, y esto se lee en una pantalla pequeña.
     */
    private static final Color TINTA = new Color(0x1F, 0x24, 0x2B);

    public byte[] generar(CredencialDTO c, PlantillaCredencial plantilla, String urlBase) {
        try {
            BufferedImage fondo = leerPlantilla(plantilla.imagen());
            // Se copia para no dibujar sobre la imagen cacheada de la plantilla.
            BufferedImage lienzo = new BufferedImage(
                    fondo.getWidth(), fondo.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = lienzo.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawImage(fondo, 0, 0, null);

            int w = lienzo.getWidth();
            int h = lienzo.getHeight();

            dibujarQr(g, plantilla.qr(), w, h, codigos.urlPublica(urlBase, c.codigo()));
            dibujarFoto(g, plantilla.foto(), w, h, c);

            // Solo el nombre y la empresa admiten dos lineas: son los unicos que se alargan.
            // Un C.I. o un numero de caseta partido en dos seria ilegible, no mas legible.
            texto(g, plantilla.nombre(), w, h, c.nombre(), plantilla, 2);
            texto(g, plantilla.empresa(), w, h, empresa(c), plantilla, 2);
            texto(g, plantilla.ci(), w, h, c.ci(), plantilla, 1);
            texto(g, plantilla.codigo(), w, h, c.casetas(), plantilla, 1);
            texto(g, plantilla.zona(), w, h, c.categoria(), plantilla, 1);

            g.dispose();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la credencial virtual", e);
        }
    }

    private static String empresa(CredencialDTO c) {
        String e = c.entidad() == null ? "" : c.entidad().trim();
        String r = c.rubro() == null ? "" : c.rubro().trim();
        return r.isEmpty() ? e : e + " / " + r;
    }

    private BufferedImage leerPlantilla(String recurso) throws IOException {
        try (InputStream in = new ClassPathResource(recurso).getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("La plantilla no es una imagen legible: " + recurso);
            return img;
        }
    }

    private void dibujarQr(Graphics2D g, Caja caja, int w, int h, String contenido) {
        if (caja == null) return;
        int lado = (int) Math.round(caja.ancho() * w);
        BarcodeQRCode qr = new BarcodeQRCode(contenido, lado, lado, null);
        java.awt.Image img = qr.createAwtImage(Color.BLACK, Color.WHITE);
        int x = (int) Math.round(caja.x() * w);
        int y = (int) Math.round(caja.y() * h);
        // Fondo blanco debajo: el QR de iText llega con el "blanco" transparente en algunos
        // casos, y un QR sobre el gris de la plantilla pierde contraste y deja de leerse.
        g.setColor(Color.WHITE);
        g.fillRect(x, y, lado, lado);
        g.drawImage(img, x, y, lado, lado, null);
    }

    /**
     * La foto, recortada en CIRCULO y rellenando el hueco sin deformarse.
     *
     * Las fotos llegan de la camara de un telefono, casi siempre verticales. Estirarlas al
     * cuadrado del hueco deja caras achatadas; aqui se recorta el centro del lado largo, que es
     * lo que hace cualquier aplicacion con las fotos de perfil.
     */
    private void dibujarFoto(Graphics2D g, Caja caja, int w, int h, CredencialDTO c) {
        if (caja == null) return;
        int ladoHueco = (int) Math.round(caja.ancho() * w);
        int xh = (int) Math.round(caja.x() * w);
        int yh = (int) Math.round(caja.y() * h);

        BufferedImage foto = leerFoto(c.fotoUrl());
        if (foto == null) {
            /*
             * SIN FOTO SE TAPA EL HUECO, no se deja como esta.
             *
             * La plantilla trae una foto de muestra impresa en el circulo —una persona
             * cualquiera—, asi que no dibujar nada no deja un hueco vacio: deja la cara de un
             * desconocido en la credencial de otro. Es peor que no tener foto, porque parece
             * un dato y no lo es. Se pone una silueta neutra que se lee como "falta la foto".
             */
            siluetaSinFoto(g, xh, yh, ladoHueco);
            return;
        }

        int lado = ladoHueco;
        int x = xh;
        int y = yh;

        BufferedImage redonda = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gf = redonda.createGraphics();
        gf.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gf.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gf.fill(new Ellipse2D.Float(0, 0, lado, lado));
        // SRC_IN: lo que se dibuje a partir de aqui solo se ve donde ya habia circulo.
        gf.setComposite(AlphaComposite.SrcIn);

        int corte = Math.min(foto.getWidth(), foto.getHeight());
        int cx = (foto.getWidth() - corte) / 2;
        int cy = (foto.getHeight() - corte) / 2;
        gf.drawImage(foto, 0, 0, lado, lado, cx, cy, cx + corte, cy + corte, null);
        gf.dispose();

        g.drawImage(redonda, x, y, null);
    }

    /**
     * La silueta de "sin foto": un circulo gris con un muñeco, del estilo de cualquier
     * aplicacion cuando no hay imagen de perfil.
     *
     * Se dibuja con dos formas y no con un archivo de imagen: es una cabeza y unos hombros, y
     * traer un PNG para eso seria un recurso mas que mantener, que puede faltar en el jar y
     * que solo se ve cuando algo esta incompleto.
     */
    private void siluetaSinFoto(Graphics2D g, int x, int y, int lado) {
        g.setColor(new Color(0xE3, 0xE7, 0xEC));
        g.fill(new Ellipse2D.Float(x, y, lado, lado));

        g.setColor(new Color(0xA8, 0xB2, 0xBF));
        // Cabeza: un circulo centrado en el tercio superior.
        float dCabeza = lado * 0.34f;
        g.fill(new Ellipse2D.Float(x + (lado - dCabeza) / 2f, y + lado * 0.17f, dCabeza, dCabeza));
        // Hombros: un ovalo ancho recortado por el borde inferior del circulo.
        java.awt.Shape antes = g.getClip();
        g.setClip(new Ellipse2D.Float(x, y, lado, lado));
        float anchoH = lado * 0.62f;
        g.fill(new Ellipse2D.Float(x + (lado - anchoH) / 2f, y + lado * 0.58f, anchoH, lado * 0.55f));
        g.setClip(antes);
    }

    /**
     * Lee una foto del disco a partir de la ruta que sirve /files/**.
     *
     * Devuelve null si no esta: los volcados de base de datos NO llevan los archivos subidos,
     * asi que en una copia de trabajo es normal que la ruta exista en la base y el archivo no.
     * Una credencial sin foto es mejor que un error.
     */
    private BufferedImage leerFoto(String fotoUrl) {
        if (fotoUrl == null || fotoUrl.isBlank()) return null;
        String relativa = fotoUrl.startsWith("/files/") ? fotoUrl.substring("/files/".length()) : fotoUrl;
        try {
            Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
            Path destino = base.resolve(relativa).normalize();
            // Sin esta comprobacion, una ruta con ".." en la base leeria cualquier archivo del
            // servidor y lo imprimiria en una credencial.
            if (!destino.startsWith(base) || !Files.isRegularFile(destino)) return null;
            return ImageIO.read(destino.toFile());
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo leer la foto {}: {}", fotoUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Un UNICO tamaño de letra para todos los valores.
     *
     * Antes cada campo se dibujaba al mayor tamaño que le cupiera, y el resultado era una
     * credencial con cuatro tamaños distintos: el C.I., que es corto, salia enorme al lado del
     * nombre de la entidad, que encogia. Se lee peor y parece descuidada. Con una medida fija
     * —una fraccion del alto de la credencial— todos los valores salen iguales, y solo se
     * encoge el que de verdad no quepa.
     */
    private static final double TAM_BASE = 0.030;   // del alto de la imagen

    /**
     * Escribe un valor dentro de su caja, en una o dos lineas.
     *
     * El nombre completo es el campo que se desborda: "AXEL RAUL DURI CHIPUNAVI" ocupa la caja
     * entera y uno mas largo se saldria. Antes la unica salida era encoger la letra hasta que
     * cupiera, y con nombres de cinco palabras eso la dejaba ilegible. Ahora se parte en dos
     * lineas por un espacio, que es lo que haria cualquiera a mano, y solo si con dos lineas
     * sigue sin caber se reduce el tamaño.
     *
     * Nunca crece de alto por su cuenta: las dos lineas se reparten el alto de la caja
     * declarada en la plantilla, asi que el texto no invade el recuadro de abajo.
     */
    private void texto(Graphics2D g, Caja caja, int w, int h, String valor,
                       PlantillaCredencial p, int maxLineas) {
        if (caja == null || valor == null || valor.isBlank()) return;
        String v = p.mayusculas() ? valor.trim().toUpperCase() : valor.trim();

        int x = (int) Math.round(caja.x() * w);
        int y = (int) Math.round(caja.y() * h);
        int ancho = (int) Math.round(caja.ancho() * w);
        int alto = (int) Math.round(caja.alto() * h);

        int tam = (int) Math.round(h * TAM_BASE);
        Font fuente = new Font(Font.SANS_SERIF, Font.BOLD, tam);
        java.util.List<String> lineas = partir(g, v, fuente, ancho, maxLineas);

        // Si ni siquiera partido entra, se encoge. Es el ultimo recurso, no el primero.
        while (tam > 12 && !cabe(g, lineas, fuente, ancho)) {
            tam -= 2;
            fuente = new Font(Font.SANS_SERIF, Font.BOLD, tam);
            lineas = partir(g, v, fuente, ancho, maxLineas);
        }

        g.setFont(fuente);
        g.setColor(TINTA);
        java.awt.FontMetrics fm = g.getFontMetrics(fuente);
        // Las lineas se centran verticalmente en la caja, juntas: con interlineado del alto
        // completo, dos lineas se salen por abajo.
        int salto = (int) Math.round(fm.getHeight() * 0.92);
        int altoTexto = fm.getAscent() - fm.getDescent() + salto * (lineas.size() - 1);
        int baseY = y + (alto + altoTexto) / 2 - salto * (lineas.size() - 1);

        for (String linea : lineas) {
            int baseX = p.centrado() ? x + (ancho - fm.stringWidth(linea)) / 2 : x;
            g.drawString(linea, baseX, baseY);
            baseY += salto;
        }
    }

    /** Reparte el texto en como mucho `maxLineas`, cortando por espacios. */
    private java.util.List<String> partir(Graphics2D g, String v, Font f, int ancho, int maxLineas) {
        java.awt.FontMetrics fm = g.getFontMetrics(f);
        if (maxLineas <= 1 || fm.stringWidth(v) <= ancho) return java.util.List.of(v);

        java.util.List<String> lineas = new java.util.ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : v.split("\\s+")) {
            String tentativa = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (fm.stringWidth(tentativa) <= ancho || actual.isEmpty()) {
                actual.setLength(0);
                actual.append(tentativa);
            } else if (lineas.size() + 1 < maxLineas) {
                lineas.add(actual.toString());
                actual.setLength(0);
                actual.append(palabra);
            } else {
                // Ya no quedan lineas: lo que falta se queda en la ultima y, si se pasa de
                // ancho, lo resolvera el encogido de fuera.
                actual.append(' ').append(palabra);
            }
        }
        lineas.add(actual.toString());
        return lineas;
    }

    private boolean cabe(Graphics2D g, java.util.List<String> lineas, Font f, int ancho) {
        java.awt.FontMetrics fm = g.getFontMetrics(f);
        return lineas.stream().allMatch(l -> fm.stringWidth(l) <= ancho);
    }
}
