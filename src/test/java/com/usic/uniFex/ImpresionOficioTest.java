package com.usic.uniFex;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.*;
import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.service.CredencialCodigoService;
import com.usic.uniFex.model.service.CredencialPdfService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class ImpresionOficioTest {
    private static final float CM = 72f / 2.54f;
    private static final float ANCHO = 21f * CM;
    private static final float ALTO = 29.7f * CM;
    private static final float CRED_W = 10f * CM;
    private static final float CRED_H = 13f * CM;
    private static final float X0 = (21f * CM - 2 * CRED_W) / 2;
    private static final float Y0 = (29.7f * CM - 2 * CRED_H) / 2;
    private final CredencialPdfService pdf = new CredencialPdfService();
    private final CredencialCodigoService codigos = new CredencialCodigoService();

    private CredencialDTO credencial(long id) {
        return new CredencialDTO(id, "FXC-PRUEBA-" + id,
                id == 1 ? "ANA MARIA FERNANDEZ CASTELLON" : "CARLOS EDUARDO PEREZ MONTERO " + id,
                "12345678", null, true,
                "ASOCIACION DE PRODUCTORES AGROPECUARIOS DEL NORTE INTEGRADO",
                "PRODUCTOS REGIONALES", "CIENCIAS ECONOMICAS Y FINANCIERAS", 1L,
                "14, 15, 16", 1L, true, false, false, null, null, false);
    }

    /** Origen del hueco i (0: arriba-izq, 1: arriba-der, 2: abajo-izq, 3: abajo-der). */
    private static float[] hueco(int i) {
        return new float[]{X0 + (i % 2) * CRED_W, i < 2 ? Y0 + CRED_H : Y0};
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void loteDe4ConFrentesYReversosDuplex(int cantidad) throws Exception {
        var lote = java.util.stream.LongStream.rangeClosed(1, cantidad).mapToObj(this::credencial).toList();
        byte[] contenido = pdf.generarGrupoDe4(lote, PlantillaCredencial.EXPOSITOR, 10, 13,
                "https://ejemplo.test", codigos);
        PdfReader lector = new PdfReader(contenido);
        try {
            assertThat(lector.getNumberOfPages()).isEqualTo(2);
            for (int pagina = 1; pagina <= 2; pagina++) {
                var hoja = lector.getPageSize(pagina);
                assertThat(hoja.getWidth()).isCloseTo(ANCHO, within(.02f));
                assertThat(hoja.getHeight()).isCloseTo(ALTO, within(.02f));
            }
            // Pagina 1: los frentes en cuadricula 2x2, cada uno de 10x14,85.
            var frente = leer(lector, 1);
            var fondos = frente.imagenes.stream()
                    .filter(m -> m.get(Matrix.I11) > 0 && Math.abs(m.get(Matrix.I11) - CRED_W) < .02f)
                    .toList();
            assertThat(fondos).hasSize(cantidad);
            for (int i = 0; i < cantidad; i++) {
                float[] h = hueco(i);
                assertThat(fondos.get(i).get(Matrix.I22)).isCloseTo(CRED_H, within(.02f));
                assertThat(fondos.get(i).get(Matrix.I31)).isCloseTo(h[0], within(.02f));
                assertThat(fondos.get(i).get(Matrix.I32)).isCloseTo(h[1], within(.02f));
            }
            for (TextRenderInfo texto : frente.textos) {
                assertThat(fondos.stream().anyMatch(fondo -> enUnaCaja(texto, fondo))).isTrue();
            }
            // Pagina 2: los reversos espejados en X (columna contraria, misma fila) para que
            // el duplex por borde largo deje cada reverso detras de su frente.
            var dorso = leer(lector, 2);
            var reversos = dorso.imagenes.stream()
                    .filter(m -> m.get(Matrix.I11) > CRED_W + .5f)
                    .toList();
            assertThat(reversos).hasSize(cantidad);
            assertThat(dorso.textos).isEmpty(); // el reverso es la imagen fija
            for (int i = 0; i < cantidad; i++) {
                Matrix fondo = fondos.get(i), reverso = reversos.get(i);
                assertThat(reverso.get(Matrix.I22)).isCloseTo(CRED_H, within(.02f));
                float centroFrenteX = fondo.get(Matrix.I31) + fondo.get(Matrix.I11) / 2;
                float centroReversoX = reverso.get(Matrix.I31) + reverso.get(Matrix.I11) / 2;
                assertThat(centroReversoX).isCloseTo(ANCHO - centroFrenteX, within(.02f));
                float centroFrenteY = fondo.get(Matrix.I32) + fondo.get(Matrix.I22) / 2;
                float centroReversoY = reverso.get(Matrix.I32) + reverso.get(Matrix.I22) / 2;
                assertThat(centroReversoY).isCloseTo(centroFrenteY, within(.02f));
            }
        } finally { lector.close(); }
        if (cantidad == 4) Files.write(Path.of("target/credenciales-oficio-muestra.pdf"), contenido);
    }

    @Test void duplexConcatenaLotesDe4EnUnSoloPdf() throws Exception {
        var lote = java.util.stream.LongStream.rangeClosed(1, 5).mapToObj(this::credencial).toList();
        byte[] contenido = pdf.generarDuplex4(lote, PlantillaCredencial.EXPOSITOR, 10, 13,
                "https://ejemplo.test", codigos);
        PdfReader lector = new PdfReader(contenido);
        try {
            // 5 credenciales = lote de 4 (pags 1-2) + lote de 1 (pags 3-4).
            assertThat(lector.getNumberOfPages()).isEqualTo(4);
            var fondos1 = leer(lector, 1).imagenes.stream()
                    .filter(m -> m.get(Matrix.I11) > 0 && Math.abs(m.get(Matrix.I11) - CRED_W) < .02f)
                    .toList();
            var fondos3 = leer(lector, 3).imagenes.stream()
                    .filter(m -> m.get(Matrix.I11) > 0 && Math.abs(m.get(Matrix.I11) - CRED_W) < .02f)
                    .toList();
            assertThat(fondos1).hasSize(4);
            assertThat(fondos3).hasSize(1);
            // El lote incompleto ocupa el primer hueco (arriba-izq) y su reverso el espejo.
            float[] h = hueco(0);
            assertThat(fondos3.get(0).get(Matrix.I31)).isCloseTo(h[0], within(.02f));
            assertThat(fondos3.get(0).get(Matrix.I32)).isCloseTo(h[1], within(.02f));
            var reversos4 = leer(lector, 4).imagenes.stream()
                    .filter(m -> m.get(Matrix.I11) > CRED_W + .5f)
                    .toList();
            assertThat(reversos4).hasSize(1);
            float centroFrenteX = fondos3.get(0).get(Matrix.I31) + fondos3.get(0).get(Matrix.I11) / 2;
            float centroReversoX = reversos4.get(0).get(Matrix.I31) + reversos4.get(0).get(Matrix.I11) / 2;
            assertThat(centroReversoX).isCloseTo(ANCHO - centroFrenteX, within(.02f));
        } finally { lector.close(); }
    }

    private boolean enUnaCaja(TextRenderInfo texto, Matrix fondo) {
        var d = PlantillaCredencial.EXPOSITOR;
        for (var caja : List.of(d.nombre(), d.empresa(), d.ci(), d.codigo(), d.zona())) {
            float x = fondo.get(Matrix.I31) + (float) caja.x() * CRED_W;
            float y = fondo.get(Matrix.I32) + (1 - (float) caja.y() - (float) caja.alto()) * CRED_H;
            float derecha = x + (float) caja.ancho() * CRED_W;
            float arriba = y + (float) caja.alto() * CRED_H;
            if (texto.getBaseline().getStartPoint().get(Vector.I1) >= x - .02f
                    && texto.getBaseline().getEndPoint().get(Vector.I1) <= derecha + .02f
                    && texto.getDescentLine().getStartPoint().get(Vector.I2) >= y - .02f
                    && texto.getAscentLine().getStartPoint().get(Vector.I2) <= arriba + .02f) return true;
        }
        return false;
    }

    private Lectura leer(PdfReader lector, int pagina) throws Exception {
        var lectura = new Lectura();
        new PdfReaderContentParser(lector).processContent(pagina, lectura);
        return lectura;
    }

    private static class Lectura implements RenderListener {
        final List<Matrix> imagenes = new ArrayList<>();
        final List<TextRenderInfo> textos = new ArrayList<>();
        public void beginTextBlock() {}
        public void endTextBlock() {}
        public void renderText(TextRenderInfo info) { textos.add(info); }
        public void renderImage(ImageRenderInfo info) { imagenes.add(info.getImageCTM()); }
    }

    @Test void fotoCircularYArchivoRotoNoInterrumpenElLote() throws Exception {
        Path carpeta = Files.createTempDirectory(Path.of("target"), "foto-oficio-");
        var foto = new java.awt.image.BufferedImage(120, 180, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var dibujo = foto.createGraphics();
        dibujo.setColor(java.awt.Color.CYAN);
        dibujo.fillRect(0, 0, 120, 180);
        dibujo.setColor(java.awt.Color.ORANGE);
        dibujo.fillOval(30, 30, 60, 60);
        dibujo.fillOval(10, 100, 100, 100);
        dibujo.dispose();
        javax.imageio.ImageIO.write(foto, "png", carpeta.resolve("foto.png").toFile());
        Files.writeString(carpeta.resolve("rota.png"), "archivo roto");
        ReflectionTestUtils.setField(pdf, "uploadRoot", carpeta.toString());
        var base = credencial(1);
        var lote = List.of("foto.png", "rota.png").stream().map(archivo -> new CredencialDTO(
                base.responsableId(), base.codigo(), base.nombre(), base.ci(), "/files/" + archivo,
                true, base.entidad(), base.rubro(), base.categoria(), 1L, base.casetas(), 1L,
                true, true, false, null, null, false)).toList();
        byte[] contenido = pdf.generarGrupoDe4(lote, PlantillaCredencial.EXPOSITOR, 10, 13, "https://ejemplo.test", codigos);
        var lector = new PdfReader(contenido);
        try {
            assertThat(lector.getNumberOfPages()).isEqualTo(2);
            // Pagina 1: 2 fondos + 2 QR + 1 foto (la rota se salta).
            assertThat(leer(lector, 1).imagenes).hasSize(5);
        } finally { lector.close(); }
        Files.write(Path.of("target/credenciales-oficio-foto.pdf"), contenido);
    }
}
