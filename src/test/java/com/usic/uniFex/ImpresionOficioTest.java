package com.usic.uniFex;

import static org.assertj.core.api.Assertions.*;
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

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void oficioDosCredencialesLadoALadoConReversoParaDoblar(int cantidad) throws Exception {
        var lote = java.util.stream.LongStream.rangeClosed(1, cantidad).mapToObj(this::credencial).toList();
        byte[] contenido = pdf.generarDosPorHoja(lote, PlantillaCredencial.EXPOSITOR, 10,
                "https://ejemplo.test", codigos);
        PdfReader lector = new PdfReader(contenido);
        try {
            // Una sola cara: cada hoja lleva 2 frentes arriba y sus 2 reversos abajo.
            assertThat(lector.getNumberOfPages()).isEqualTo((cantidad + 1) / 2);
            float xIzq = (21.6f * CM - 2 * 10 * CM) / 2;
            float yAlta = (33f * CM - 2 * 15 * CM) / 2 + 15 * CM;
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                var hoja = lector.getPageSize(pagina);
                assertThat(hoja.getWidth()).isCloseTo(21.6f * CM, within(.02f));
                assertThat(hoja.getHeight()).isCloseTo(33f * CM, within(.02f));
                var lectura = leer(lector, pagina);
                int esperadas = Math.min(2, cantidad - (pagina - 1) * 2);
                var fondos = lectura.imagenes.stream()
                        .filter(m -> m.get(Matrix.I11) > 0 && Math.abs(m.get(Matrix.I11) - 10 * CM) < .02f)
                        .toList();
                // El reverso es mas ancho (cover de 1183x1535 sobre 10x15): la unica imagen
                // grande que no es un frente.
                var reversos = lectura.imagenes.stream()
                        .filter(m -> m.get(Matrix.I11) > 10 * CM + .5f)
                        .toList();
                assertThat(fondos).hasSize(esperadas);
                assertThat(reversos).hasSize(esperadas);
                for (int i = 0; i < esperadas; i++) {
                    Matrix fondo = fondos.get(i), dorso = reversos.get(i);
                    // Frente de 10x15 arriba, en su columna.
                    assertThat(fondo.get(Matrix.I22)).isCloseTo(15 * CM, within(.02f));
                    assertThat(fondo.get(Matrix.I31)).isCloseTo(xIzq + i * 10 * CM, within(.02f));
                    assertThat(fondo.get(Matrix.I32)).isCloseTo(yAlta, within(.02f));
                    // Reverso cubriendo el mismo 10x15 abajo, con los pixeles ya girados 180°
                    // para que al doblar quede derecho: comparte el centro X con su frente
                    // y es simetrico en Y respecto a la doblez (mitad de la hoja).
                    assertThat(dorso.get(Matrix.I11)).isGreaterThanOrEqualTo(10 * CM - .02f);
                    assertThat(dorso.get(Matrix.I22)).isCloseTo(15 * CM, within(.02f));
                    assertThat(dorso.get(Matrix.I31) + dorso.get(Matrix.I11) / 2)
                            .isCloseTo(fondo.get(Matrix.I31) + fondo.get(Matrix.I11) / 2, within(.02f));
                    float centroFrente = fondo.get(Matrix.I32) + fondo.get(Matrix.I22) / 2;
                    float centroDorso = dorso.get(Matrix.I32) + dorso.get(Matrix.I22) / 2;
                    assertThat((centroFrente + centroDorso) / 2)
                            .isCloseTo(hoja.getHeight() / 2, within(.02f));
                }
                // Todo texto es del frente (mitad superior) o el rotulo de doblez del margen.
                boolean conRotulo = false;
                for (TextRenderInfo texto : lectura.textos) {
                    if (texto.getText().trim().equals("DOBLAR")) {
                        assertThat(texto.getBaseline().getStartPoint().get(Vector.I1))
                                .isCloseTo(xIzq, within(30f));
                        conRotulo = true;
                        continue;
                    }
                    assertThat(texto.getBaseline().getStartPoint().get(Vector.I2))
                            .isGreaterThan(hoja.getHeight() / 2);
                    assertThat(fondos.stream().anyMatch(fondo -> enUnaCaja(texto, fondo))).isTrue();
                }
                assertThat(conRotulo).isTrue();
            }
        } finally { lector.close(); }
        if (cantidad == 3) Files.write(Path.of("target/credenciales-oficio-muestra.pdf"), contenido);
    }

    private boolean enUnaCaja(TextRenderInfo texto, Matrix fondo) {
        var d = PlantillaCredencial.EXPOSITOR;
        for (var caja : List.of(d.nombre(), d.empresa(), d.ci(), d.codigo(), d.zona())) {
            float x = fondo.get(Matrix.I31) + (float) caja.x() * 10 * CM;
            float y = fondo.get(Matrix.I32) + (1 - (float) caja.y() - (float) caja.alto()) * 15 * CM;
            float derecha = x + (float) caja.ancho() * 10 * CM;
            float arriba = y + (float) caja.alto() * 15 * CM;
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
        byte[] contenido = pdf.generarDosPorHoja(lote, PlantillaCredencial.EXPOSITOR, 10, "https://ejemplo.test", codigos);
        var lector = new PdfReader(contenido);
        try {
            assertThat(lector.getNumberOfPages()).isEqualTo(1);
            // 2 fondos + 2 QR + 1 foto (la rota se salta) + 2 reversos.
            assertThat(leer(lector, 1).imagenes).hasSize(7);
        } finally { lector.close(); }
        Files.write(Path.of("target/credenciales-oficio-foto.pdf"), contenido);
    }
}
