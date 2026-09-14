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

            texto(g, plantilla.nombre(), w, h, c.nombre(), plantilla);
            texto(g, plantilla.empresa(), w, h, empresa(c), plantilla);
            texto(g, plantilla.ci(), w, h, c.ci(), plantilla);
            texto(g, plantilla.codigo(), w, h, c.casetas(), plantilla);
            texto(g, plantilla.zona(), w, h, c.categoria(), plantilla);

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
        BufferedImage foto = leerFoto(c.fotoUrl());
        if (foto == null) return;   // sin foto se deja el hueco de la plantilla, no un cuadro gris

        int lado = (int) Math.round(caja.ancho() * w);
        int x = (int) Math.round(caja.x() * w);
        int y = (int) Math.round(caja.y() * h);

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
     * Escribe un valor dentro de su caja, encogiendo la letra hasta que quepa.
     *
     * Los nombres de entidad de esta feria llegan a los 60 caracteres. Con un tamaño fijo se
     * salian de la caja y pisaban la siguiente; recortarlos con "..." esconderia justo el dato
     * que se viene a leer.
     */
    private void texto(Graphics2D g, Caja caja, int w, int h, String valor, PlantillaCredencial p) {
        if (caja == null || valor == null || valor.isBlank()) return;
        String v = p.mayusculas() ? valor.trim().toUpperCase() : valor.trim();

        int x = (int) Math.round(caja.x() * w);
        int y = (int) Math.round(caja.y() * h);
        int ancho = (int) Math.round(caja.ancho() * w);
        int alto = (int) Math.round(caja.alto() * h);

        /*
         * Se empieza por el 72% del alto de la caja, no por el alto entero.
         *
         * Con el alto completo, un valor corto como un C.I. salia a tamaño maximo y uno largo
         * como el nombre de la entidad encogia mucho: la credencial quedaba con cuatro tamaños
         * distintos y se veia descuidada. El tope deja aire arriba y abajo y hace que los
         * valores cortos —que son la mayoria— salgan todos iguales.
         */
        int tam = Math.max(12, (int) Math.round(alto * 0.72));
        Font fuente;
        java.awt.FontMetrics fm;
        do {
            fuente = new Font(Font.SANS_SERIF, Font.BOLD, tam);
            fm = g.getFontMetrics(fuente);
            if (fm.stringWidth(v) <= ancho) break;
            tam -= 2;
        } while (tam > 10);

        g.setFont(fuente);
        g.setColor(TINTA);
        int baseY = y + (alto + fm.getAscent() - fm.getDescent()) / 2;
        int baseX = p.centrado() ? x + (ancho - fm.stringWidth(v)) / 2 : x;
        g.drawString(v, baseX, baseY);
    }
}
