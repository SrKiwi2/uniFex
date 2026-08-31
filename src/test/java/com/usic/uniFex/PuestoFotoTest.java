package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.PuestoFotoService;
import com.usic.uniFex.model.service.PuestoMapaService;

/**
 * Fotos y referencia de ubicacion de las casetas (Bloque 2).
 *
 * Lo que se protege: que una foto se pueda asignar a VARIAS casetas de una vez —sin eso la
 * funcionalidad no se usa, porque nadie sube la misma imagen 40 veces— y que borrarla de una
 * caseta no deje sin foto a las demas que comparten el archivo.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PuestoFotoTest {

    private static final Long USUARIO = 1L;

    @Autowired private PuestoFotoService fotos;
    @Autowired private PuestoMapaService mapa;
    @Autowired private IPuestoDao puestoDao;
    @Autowired private JdbcTemplate jdbc;

    private List<Long> casetas;

    @BeforeEach
    void setUp() {
        casetas = puestoDao.listarPuestos().stream().limit(3).map(Puesto::getId).toList();
        Assumptions.assumeTrue(casetas.size() == 3, "hacen falta 3 casetas libres");
        limpiar();
    }

    @AfterEach
    void tearDown() {
        limpiar();
        for (Long id : casetas) {
            jdbc.update("UPDATE puesto SET referencia = NULL WHERE id = ?", id);
        }
    }

    private void limpiar() {
        for (Long id : casetas) {
            jdbc.update("DELETE FROM puesto_foto WHERE id_puesto = ?", id);
        }
    }

    private MockMultipartFile imagen() {
        return new MockMultipartFile("archivo", "caseta.png", "image/png", new byte[] { 1, 2, 3, 4 });
    }

    @Test
    void unaFotoSeAsignaAVariasCasetasYElArchivoSeGuardaUnaSolaVez() {
        PuestoFotoService.Resultado r = fotos.subir(casetas, imagen(), "Vista frontal", USUARIO);

        assertThat(r.ok()).as(r.mensaje()).isTrue();
        assertThat(r.puestosAfectados()).containsExactlyInAnyOrderElementsOf(casetas);

        // Las tres apuntan al MISMO archivo: subirlo por caseta multiplicaria el disco sin
        // aportar nada, porque es la misma imagen.
        List<String> rutas = jdbc.queryForList(
                "SELECT DISTINCT ruta FROM puesto_foto WHERE id_puesto IN (?,?,?)",
                String.class, casetas.get(0), casetas.get(1), casetas.get(2));
        assertThat(rutas).hasSize(1);
        assertThat(rutas.get(0)).startsWith("puestos/");

        // Y cada una la ve como suya.
        for (Long id : casetas) {
            assertThat(fotos.fotosDe(id)).hasSize(1);
        }
        assertThat(fotos.idsConFoto()).containsAll(casetas);
    }

    @Test
    void borrarLaFotoDeUnaCasetaNoDejaSinFotoALasQueCompartenElArchivo() {
        fotos.subir(casetas, imagen(), null, USUARIO);
        Long primera = casetas.get(0);
        Long idFoto = fotos.fotosDe(primera).get(0).getId();

        assertThat(fotos.borrar(idFoto)).isTrue();

        assertThat(fotos.fotosDe(primera)).isEmpty();
        assertThat(fotos.fotosDe(casetas.get(1)))
                .as("las demas conservan la suya aunque sea el mismo archivo").hasSize(1);
    }

    @Test
    void respetaElMaximoDeFotosPorCaseta() {
        Long id = casetas.get(0);
        for (int i = 0; i < PuestoFotoService.MAX_POR_CASETA; i++) {
            assertThat(fotos.subir(List.of(id), imagen(), null, USUARIO).ok()).isTrue();
        }
        assertThat(fotos.fotosDe(id)).hasSize(PuestoFotoService.MAX_POR_CASETA);

        PuestoFotoService.Resultado extra = fotos.subir(List.of(id), imagen(), null, USUARIO);
        assertThat(extra.ok()).isFalse();
        assertThat(extra.mensaje()).contains("maximo");
        assertThat(fotos.fotosDe(id)).hasSize(PuestoFotoService.MAX_POR_CASETA);
    }

    @Test
    void laReferenciaSeGuardaYUnTextoVacioSeConvierteEnNulo() {
        Long id = casetas.get(0);

        assertThat(mapa.cambiarReferencia(id, "  Frente a la puerta 3  ", USUARIO)).isTrue();
        assertThat(jdbc.queryForObject("SELECT referencia FROM puesto WHERE id=?", String.class, id))
                .as("se guarda sin espacios sobrantes").isEqualTo("Frente a la puerta 3");

        // Vaciar el campo debe dejar NULL, no una cadena vacia: si no, "sin referencia"
        // tendria dos representaciones distintas y cada consulta tendria que cubrir las dos.
        assertThat(mapa.cambiarReferencia(id, "   ", USUARIO)).isTrue();
        assertThat(jdbc.queryForObject("SELECT referencia FROM puesto WHERE id=?", String.class, id))
                .isNull();
    }
}
