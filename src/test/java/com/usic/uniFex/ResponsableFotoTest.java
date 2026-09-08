package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
import com.usic.uniFex.model.dto.ResponsableFotoDTO;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.RegistroVentaService;
import com.usic.uniFex.model.service.RegistroVentaService.DatosPersona;
import com.usic.uniFex.model.service.RegistroVentaService.NuevaVenta;
import com.usic.uniFex.model.service.ResponsableFotoService;

/**
 * Fotos de los responsables: lo que despues va impreso en su credencial.
 *
 * Lo que se protege aqui es la regla que separa a un vendedor de otro. Los endpoints validan
 * el permiso sobre la INSCRIPCION, asi que si el servicio no comprobara ademas que el
 * responsable pertenece a esa venta, cualquiera con una venta propia podria cambiarle la foto
 * al responsable de otro pasando su id — y esa foto acaba en una credencial de acceso.
 *
 * Ejecutar:  mvnw.cmd test "-Dtest=ResponsableFotoTest"
 */
@SpringBootTest
@ActiveProfiles("dev")
class ResponsableFotoTest {

    private static final String ENTIDAD_A = "ENTIDAD FOTO PRUEBA A";
    private static final String ENTIDAD_B = "ENTIDAD FOTO PRUEBA B";

    @Autowired private RegistroVentaService registro;
    @Autowired private ResponsableFotoService fotos;
    @Autowired private IPuestoDao puestoDao;
    @Autowired private JdbcTemplate jdbc;

    private List<Puesto> libres;

    @BeforeEach
    void setUp() {
        libres = puestoDao.listarPuestos().stream()
                .filter(p -> Puesto.LIBRE.equals(p.getEstadoPuesto()))
                .limit(2)
                .toList();
        Assumptions.assumeTrue(libres.size() == 2, "hacen falta 2 casetas libres");
        limpiar();
    }

    @AfterEach
    void tearDown() {
        limpiar();
        for (Puesto p : libres) {
            jdbc.update("UPDATE puesto SET estado_puesto='L', reservado_por_id_usuario=NULL, "
                    + "reserva_expira=NULL WHERE id=?", p.getId());
        }
    }

    private void limpiar() {
        for (String nombre : List.of(ENTIDAD_A, ENTIDAD_B)) {
            jdbc.update("DELETE FROM auditoria WHERE tabla='inscripcion' AND id_registro IN "
                    + "(SELECT i.id FROM inscripcion i JOIN entidad e ON e.id=i.id_entidad WHERE e.nombre=?)",
                    nombre);
            jdbc.update("DELETE FROM inscripcion_puesto WHERE id_inscripcion IN "
                    + "(SELECT i.id FROM inscripcion i JOIN entidad e ON e.id=i.id_entidad WHERE e.nombre=?)",
                    nombre);
            jdbc.update("DELETE FROM inscripcion WHERE id_entidad IN (SELECT id FROM entidad WHERE nombre=?)",
                    nombre);
            List<Long> personas = jdbc.queryForList(
                    "SELECT r.id_persona FROM responsable r JOIN entidad e ON e.id=r.id_entidad WHERE e.nombre=?",
                    Long.class, nombre);
            jdbc.update("DELETE FROM responsable WHERE id_entidad IN (SELECT id FROM entidad WHERE nombre=?)",
                    nombre);
            for (Long idPersona : personas) jdbc.update("DELETE FROM persona WHERE id=?", idPersona);
            jdbc.update("DELETE FROM entidad WHERE nombre=?", nombre);
        }
    }

    private Long registrarVenta(String entidad, Long puestoId) {
        NuevaVenta v = new NuevaVenta(
                entidad, "123", "Desc", "Objeto", "Rep Legal", "999",
                jdbc.queryForObject("SELECT id FROM tipo_entidad ORDER BY id LIMIT 1", Long.class),
                LocalDate.now(), LocalDate.now().plusDays(3),
                List.of(new DatosPersona("TITULAR", "Perez", "Lopez", "111", "t@x.com", "700"),
                        new DatosPersona("AYUDANTE", "Gomez", "Ruiz", "222", "a@x.com", "701")),
                "Banco X", 1L, true, List.of(puestoId));
        RegistroVentaService.Resultado r = registro.registrar(v, 1L);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        return r.inscripcionId();
    }

    private MockMultipartFile imagen() {
        // PNG minimo valido: basta para ejercitar el guardado, no se valida el contenido.
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        return new MockMultipartFile("archivo", "foto.png", "image/png", png);
    }

    @Test
    void seSubeLaFotoYLaVentaPasaATenerlasCompletas() {
        Long inscripcion = registrarVenta(ENTIDAD_A, libres.get(0).getId());

        List<ResponsableFotoDTO> antes = fotos.listar(inscripcion);
        assertThat(antes).hasSize(2);
        assertThat(antes).allSatisfy(r -> assertThat(r.tieneFoto()).isFalse());
        // Una venta sin ninguna foto no puede emitir credenciales.
        assertThat(fotos.fotosCompletas(inscripcion)).isFalse();

        // El titular tiene que salir primero: es el orden con el que se revisa en la feria.
        assertThat(antes.get(0).esTitular()).isTrue();

        fotos.guardar(inscripcion, antes.get(0).id(), imagen(), 1L);
        assertThat(fotos.fotosCompletas(inscripcion))
                .as("con una sola foto todavia falta la del acompaniante").isFalse();

        fotos.guardar(inscripcion, antes.get(1).id(), imagen(), 1L);
        assertThat(fotos.fotosCompletas(inscripcion)).isTrue();

        List<ResponsableFotoDTO> despues = fotos.listar(inscripcion);
        assertThat(despues).allSatisfy(r -> {
            assertThat(r.tieneFoto()).isTrue();
            // La url tiene que servir por /files/**, no ser la ruta cruda del disco.
            assertThat(r.fotoUrl()).startsWith("/files/responsables/");
        });
    }

    @Test
    void quitarLaFotoDejaLaVentaIncompletaDeNuevo() {
        Long inscripcion = registrarVenta(ENTIDAD_A, libres.get(0).getId());
        List<ResponsableFotoDTO> rs = fotos.listar(inscripcion);
        for (ResponsableFotoDTO r : rs) fotos.guardar(inscripcion, r.id(), imagen(), 1L);
        assertThat(fotos.fotosCompletas(inscripcion)).isTrue();

        ResponsableFotoService.Resultado r = fotos.quitar(inscripcion, rs.get(0).id(), 1L);
        assertThat(r.ok()).isTrue();
        assertThat(fotos.fotosCompletas(inscripcion)).isFalse();
    }

    /**
     * La comprobacion que impide cambiarle la foto al cliente de otro vendedor. El permiso del
     * endpoint se valida sobre la inscripcion, asi que sin esto bastaria con tener una venta
     * propia y adivinar el id del responsable ajeno.
     */
    @Test
    void noSePuedeTocarUnResponsableDeOtraVenta() {
        Long ventaA = registrarVenta(ENTIDAD_A, libres.get(0).getId());
        Long ventaB = registrarVenta(ENTIDAD_B, libres.get(1).getId());

        Long responsableDeB = fotos.listar(ventaB).get(0).id();

        ResponsableFotoService.Resultado r = fotos.guardar(ventaA, responsableDeB, imagen(), 1L);
        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("no es de esta venta");

        // Y no se le coló la foto por el camino.
        assertThat(fotos.listar(ventaB).get(0).tieneFoto()).isFalse();

        assertThat(fotos.quitar(ventaA, responsableDeB, 1L).ok()).isFalse();
    }

    @Test
    void unaImagenVaciaSeRechaza() {
        Long inscripcion = registrarVenta(ENTIDAD_A, libres.get(0).getId());
        Long responsable = fotos.listar(inscripcion).get(0).id();

        ResponsableFotoService.Resultado r = fotos.guardar(inscripcion, responsable,
                new MockMultipartFile("archivo", "vacio.png", "image/png", new byte[0]), 1L);
        assertThat(r.ok()).isFalse();
        assertThat(fotos.listar(inscripcion).get(0).tieneFoto()).isFalse();
    }

    @Test
    void sinFotosNoSePuedeEmitirYConTodasSi() {
        Long inscripcion = registrarVenta(ENTIDAD_A, libres.get(0).getId());
        assertThat(fotos.fotosCompletas(inscripcion)).isFalse();
        for (ResponsableFotoDTO r : fotos.listar(inscripcion)) {
            fotos.guardar(inscripcion, r.id(), imagen(), 1L);
        }
        assertThat(fotos.fotosCompletas(inscripcion)).isTrue();

        // La foto queda registrada en la persona, no en el vinculo: es la cara de alguien.
        Integer conFoto = jdbc.queryForObject(
                "SELECT count(*) FROM persona p JOIN responsable r ON r.id_persona=p.id "
                + "JOIN entidad e ON e.id=r.id_entidad WHERE e.nombre=? AND p.foto IS NOT NULL",
                Integer.class, ENTIDAD_A);
        assertThat(conFoto).isEqualTo(2);
    }

    @Test
    void unaVentaSinResponsablesNoCuentaComoCompleta() {
        // `fotosCompletas` sobre una venta inexistente no debe decir "si" por vacuidad: un
        // allMatch sobre lista vacia es true, y eso habilitaria emitir credenciales de nada.
        assertThat(fotos.fotosCompletas(-1L)).isFalse();
    }

}
