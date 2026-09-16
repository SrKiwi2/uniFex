package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.dto.NocheFexpoDTO;
import com.usic.uniFex.model.service.NochesFexpoService;
import com.usic.uniFex.model.service.VisitasPaginaService;

/**
 * Musica de las noches y contador de visitas de la vista publica (V38).
 *
 * Lo que se protege: que la "musica" solo pueda ser un MP3 —el bucket de noches acepta tambien
 * fotos y videos, asi que el filtro general no basta— y que el contador no pierda visitas
 * cuando llegan varias a la vez, que es justo cuando mas gente mira la pagina.
 */
@SpringBootTest
@ActiveProfiles("dev")
class NochesMusicaYVisitasTest {

    private static final Long USUARIO = 1L;
    /** Clave propia: la prueba nunca toca el contador real de la feria. */
    private static final String PAGINA_PRUEBA = "prueba-automatica-visitas";

    @Autowired private NochesFexpoService noches;
    @Autowired private VisitasPaginaService visitas;
    @Autowired private JdbcTemplate jdbc;

    private Long nocheId;

    @BeforeEach
    void setUp() {
        NochesFexpoService.Resultado r = noches.crear(new NochesFexpoService.Datos(
                LocalDate.now(), "NOCHE DE PRUEBA AUTOMATICA", null, null, null, 0), USUARIO);
        Assumptions.assumeTrue(r.ok(), "hace falta una edicion activa: " + r.mensaje());
        nocheId = r.noche().getId();
        jdbc.update("DELETE FROM contador_visitas WHERE pagina = ?", PAGINA_PRUEBA);
    }

    @AfterEach
    void tearDown() {
        if (nocheId != null) jdbc.update("DELETE FROM noche_fexpo WHERE id = ?", nocheId);
        jdbc.update("DELETE FROM contador_visitas WHERE pagina = ?", PAGINA_PRUEBA);
    }

    private MockMultipartFile mp3() {
        return new MockMultipartFile("archivo", "tema.mp3", "audio/mpeg", new byte[] { 'I', 'D', '3', 4 });
    }

    @Test
    void unMp3SeGuardaYViajaEnElDto() {
        NochesFexpoService.Resultado r = noches.subirAudio(nocheId, mp3(), USUARIO);

        assertThat(r.ok()).as(r.mensaje()).isTrue();
        assertThat(r.noche().getAudioArchivo()).startsWith("noches/").endsWith(".mp3");
        NocheFexpoDTO dto = NocheFexpoDTO.de(r.noche());
        assertThat(dto.urlAudio()).isEqualTo("/files/" + r.noche().getAudioArchivo());
        // La musica no toca el fondo de la tarjeta: son dos archivos independientes.
        assertThat(dto.urlMedio()).isNull();
    }

    @Test
    void rechazaComoMusicaLoQueNoEsMp3() {
        // Una imagen pasaria el filtro general de FileStorageService (el bucket acepta fotos):
        // tiene que ser el servicio de noches quien la frene.
        MockMultipartFile png = new MockMultipartFile("archivo", "foto.png", "image/png", new byte[] { 1, 2, 3 });

        NochesFexpoService.Resultado r = noches.subirAudio(nocheId, png, USUARIO);

        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("MP3");
        assertThat(jdbc.queryForObject("SELECT audio_archivo FROM noche_fexpo WHERE id = ?", String.class, nocheId))
                .isNull();
    }

    @Test
    void quitarLaMusicaDejaLaNocheSinAudio() {
        assertThat(noches.subirAudio(nocheId, mp3(), USUARIO).ok()).isTrue();

        NochesFexpoService.Resultado r = noches.quitarAudio(nocheId, USUARIO);

        assertThat(r.ok()).isTrue();
        assertThat(NocheFexpoDTO.de(r.noche()).urlAudio()).isNull();
    }

    @Test
    void lasVisitasSimultaneasNoSePierden() throws Exception {
        // El total se incrementa en la BD con una sola sentencia: 20 visitas a la vez tienen
        // que dar exactamente 20. Leer, sumar en Java y guardar perderia algunas.
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Long>> pendientes = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            pendientes.add(pool.submit(() -> {
                salida.await();
                return visitas.registrar(PAGINA_PRUEBA);
            }));
        }
        salida.countDown();
        List<Long> totales = new ArrayList<>();
        for (Future<Long> f : pendientes) totales.add(f.get(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(visitas.total(PAGINA_PRUEBA)).isEqualTo(hilos);
        // Y cada visita recibio un total distinto, 1..20: ninguna leyo el mismo valor que otra.
        assertThat(totales).containsExactlyInAnyOrderElementsOf(
                LongStream.rangeClosed(1, hilos).boxed().toList());
    }

    @Test
    void unaPaginaSinVisitasDaCero() {
        assertThat(visitas.total(PAGINA_PRUEBA)).isZero();
    }
}
