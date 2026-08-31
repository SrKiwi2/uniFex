package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.CancelarInscripcionService;
import com.usic.uniFex.model.service.PuestoReservaService;
import com.usic.uniFex.model.service.RegistroVentaService;
import com.usic.uniFex.model.service.SolicitudCancelacionService;

/**
 * Cancelacion de una venta, contra la copia LOCAL (perfil dev).
 *
 * Flujo (V11): el vendedor SOLICITA cancelar con un motivo, administracion aprueba
 * (o rechaza), y solo entonces se puede cancelar. Lo que se protege: cancelar debe
 * **liberar las casetas** (vuelven a 'L', el mapa las muestra disponibles), **pasar la
 * inscripcion al historico**, y un vendedor SIN solicitud aprobada NO puede cancelar
 * (si intenta, no se toca nada). El admin si puede cancelar directo.
 *
 * Ejecutar:  mvnw.cmd test "-Dtest=CancelarInscripcionTest"
 */
@SpringBootTest
@ActiveProfiles("dev")
class CancelarInscripcionTest {

    private static final String NOMBRE_PRUEBA = "ENTIDAD DE PRUEBA CANCELACION";

    @Autowired private RegistroVentaService registro;
    @Autowired private CancelarInscripcionService cancelar;
    @Autowired private SolicitudCancelacionService solicitudes;
    @Autowired private PuestoReservaService reserva;
    @Autowired private IPuestoDao puestoDao;
    @Autowired private IInscripcionDao inscripcionDao;
    @Autowired private JdbcTemplate jdbc;

    private List<Puesto> libres;
    private Long categoriaId;
    private BigDecimal precioOriginal;

    @BeforeEach
    void setUp() {
        // Las dos casetas deben ser de LA MISMA categoria, igual que en RegistroVentaTest.
        List<Puesto> todas = puestoDao.listarPuestos();
        libres = todas.stream()
                .filter(p -> p.getCategoria() != null)
                .collect(java.util.stream.Collectors.groupingBy(p -> p.getCategoria().getId()))
                .values().stream()
                .filter(g -> g.size() >= 2)
                .findFirst()
                .map(g -> g.subList(0, 2))
                .orElse(List.of());
        Assumptions.assumeTrue(libres.size() == 2,
                "hacen falta 2 casetas libres de la misma categoria para la prueba");

        categoriaId = libres.get(0).getCategoria().getId();
        precioOriginal = jdbc.queryForObject(
                "SELECT precio_base FROM categoria WHERE id=?", BigDecimal.class, categoriaId);
        jdbc.update("UPDATE categoria SET precio_base=? WHERE id=?", new BigDecimal("100.00"), categoriaId);

        limpiarDatosDePrueba();
    }

    @AfterEach
    void tearDown() {
        limpiarDatosDePrueba();
        for (Puesto p : libres) {
            jdbc.update("UPDATE puesto SET estado_puesto='L', reservado_por_id_usuario=NULL, "
                    + "reserva_expira=NULL WHERE id=?", p.getId());
        }
        jdbc.update("UPDATE categoria SET precio_base=? WHERE id=?", precioOriginal, categoriaId);
    }

    /** Borra lo que crea la prueba, respetando el orden de las claves foraneas. */
    private void limpiarDatosDePrueba() {
        // auditoria no tiene FK a proposito: hay que borrar sus huellas a mano.
        jdbc.update("DELETE FROM auditoria WHERE tabla='inscripcion' AND id_registro IN "
                + "(SELECT i.id FROM inscripcion i JOIN entidad e ON e.id=i.id_entidad WHERE e.nombre=?)",
                NOMBRE_PRUEBA);
        jdbc.update("DELETE FROM solicitud_cancelacion WHERE id_inscripcion IN "
                + "(SELECT i.id FROM inscripcion i JOIN entidad e ON e.id=i.id_entidad WHERE e.nombre=?)",
                NOMBRE_PRUEBA);
        jdbc.update("DELETE FROM inscripcion_puesto WHERE id_inscripcion IN "
                + "(SELECT i.id FROM inscripcion i JOIN entidad e ON e.id=i.id_entidad WHERE e.nombre=?)",
                NOMBRE_PRUEBA);
        jdbc.update("DELETE FROM inscripcion WHERE id_entidad IN (SELECT id FROM entidad WHERE nombre=?)",
                NOMBRE_PRUEBA);
        List<Long> personas = jdbc.queryForList(
                "SELECT r.id_persona FROM responsable r JOIN entidad e ON e.id=r.id_entidad "
                + "WHERE e.nombre=?", Long.class, NOMBRE_PRUEBA);
        jdbc.update("DELETE FROM responsable WHERE id_entidad IN (SELECT id FROM entidad WHERE nombre=?)",
                NOMBRE_PRUEBA);
        for (Long idPersona : personas) {
            jdbc.update("DELETE FROM persona WHERE id=?", idPersona);
        }
        jdbc.update("DELETE FROM entidad WHERE nombre=?", NOMBRE_PRUEBA);
    }

    private RegistroVentaService.NuevaVenta venta(List<Long> puestos) {
        return new RegistroVentaService.NuevaVenta(
                NOMBRE_PRUEBA, "123456789", "Descripcion", "Objeto",
                "Representante Legal", "99887766",
                jdbc.queryForObject("SELECT id FROM tipo_entidad ORDER BY id LIMIT 1", Long.class),
                LocalDate.now(), LocalDate.now().plusDays(5),
                List.of(new RegistroVentaService.DatosPersona("TITULAR", "Perez", "Lopez", "111", "t@x.com", "700")),
                null, null, false,
                puestos);
    }

    /** Registra una venta de las dos casetas de la prueba. Devuelve el id de la inscripcion. */
    private Long venderUnaVenta() {
        RegistroVentaService.Resultado r =
                registro.registrar(venta(libres.stream().map(Puesto::getId).toList()), 1L);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        return r.inscripcionId();
    }

    /** El flujo completo del vendedor (V11): solicitar + aprobar + cancelar. */
    private Long aprobarCancelacionDe(Long inscripcionId) {
        assertThat(solicitudes.solicitar(inscripcionId, "El cliente desistio", 1L, "WEB").ok()).isTrue();
        Long solicitudId = solicitudes.pendientes().stream()
                .filter(s -> s.inscripcionId().equals(inscripcionId))
                .findFirst().orElseThrow().id();
        assertThat(solicitudes.aprobar(solicitudId, 2L, "WEB").ok()).isTrue();
        return solicitudId;
    }

    private int auditoria(String accion, Long inscripcionId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE tabla='inscripcion' "
                        + "AND id_registro=? AND accion=?",
                Integer.class, inscripcionId, accion);
    }

    @Test
    void cancelarLiberaLasCasetasYMarcaElHistorico() {
        Long id = venderUnaVenta();
        List<Long> ids = libres.stream().map(Puesto::getId).toList();
        aprobarCancelacionDe(id);

        CancelarInscripcionService.Resultado r =
                cancelar.cancelar(id, null, 1L, "WEB", false);

        assertThat(r.ok()).as(r.mensaje()).isTrue();
        assertThat(r.puestosLiberados()).containsExactlyInAnyOrderElementsOf(ids);

        // Las casetas volvieron a LIBRE: el mapa las muestra disponibles.
        for (Long pid : ids) {
            assertThat(puestoDao.findById(pid).orElseThrow().getEstadoPuesto())
                    .isEqualTo(Puesto.LIBRE);
        }

        // La inscripcion quedo en el historico con motivo, quien, cuando y desde donde.
        Inscripcion i = inscripcionDao.findById(id).orElseThrow();
        assertThat(i.getEstado()).isEqualTo("X");
        assertThat(i.getMotivoCancelacion()).isEqualTo("El cliente desistio");
        assertThat(i.getFechaCancelacion()).isNotNull();
        assertThat(i.getCanceladaPorIdUsuario()).isEqualTo(1L);
        assertThat(i.getOrigenCancelacion()).isEqualTo("WEB");

        // Huella de auditoria del ciclo de vida: registro, solicitud, aprobacion y cancelacion.
        assertThat(auditoria("REGISTRO", id)).isEqualTo(1);
        assertThat(auditoria("SOLICITUD_CANCELACION", id)).isEqualTo(1);
        assertThat(auditoria("APROBACION_CANCELACION", id)).isEqualTo(1);
        assertThat(auditoria("CANCELACION", id)).isEqualTo(1);
    }

    @Test
    void sinSolicitudAprobadaElVendedorNoPuedeCancelar() {
        Long id = venderUnaVenta();

        CancelarInscripcionService.Resultado r =
                cancelar.cancelar(id, "Quiero cancelar", 1L, "WEB", false);

        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("solicitud aprobada");

        // Ni las casetas ni la inscripcion se tocaron.
        for (Puesto p : libres) {
            assertThat(puestoDao.findById(p.getId()).orElseThrow().getEstadoPuesto())
                    .isEqualTo(Puesto.OCUPADO);
        }
        assertThat(inscripcionDao.findById(id).orElseThrow().getEstado()).isEqualTo("ACTIVO");
        assertThat(auditoria("CANCELACION", id)).isZero();
    }

    @Test
    void elAdminSiPuedeCancelarDirectoConMotivo() {
        Long id = venderUnaVenta();

        CancelarInscripcionService.Resultado r =
                cancelar.cancelar(id, "Decision de administracion", 2L, "WEB", true);

        assertThat(r.ok()).as(r.mensaje()).isTrue();
        assertThat(inscripcionDao.findById(id).orElseThrow().getMotivoCancelacion())
                .isEqualTo("Decision de administracion");
        // Sin solicitud previa: solo queda la huella de la cancelacion del admin.
        assertThat(auditoria("SOLICITUD_CANCELACION", id)).isZero();
        assertThat(auditoria("CANCELACION", id)).isEqualTo(1);
    }

    @Test
    void unaVentaYaCanceladaNoSeCancelaDosVeces() {
        Long id = venderUnaVenta();
        aprobarCancelacionDe(id);
        assertThat(cancelar.cancelar(id, null, 1L, "WEB", false).ok()).isTrue();

        CancelarInscripcionService.Resultado r = cancelar.cancelar(id, null, 1L, "WEB", false);

        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("ya esta cancelada");
        // El motivo sigue siendo el de la solicitud, y no hay una segunda huella.
        assertThat(inscripcionDao.findById(id).orElseThrow().getMotivoCancelacion())
                .isEqualTo("El cliente desistio");
        assertThat(auditoria("CANCELACION", id)).isEqualTo(1);
    }

    @Test
    void laCasetaLiberadaSePuedeVolverAVender() {
        Long id = venderUnaVenta();
        aprobarCancelacionDe(id);

        // Desde el APK: el origen queda registrado en la auditoria.
        assertThat(cancelar.cancelar(id, null, 1L, "APK", false).ok()).isTrue();
        assertThat(inscripcionDao.findById(id).orElseThrow().getOrigenCancelacion())
                .isEqualTo("APK");

        // Tras la cancelacion la caseta vuelve al ciclo normal: otro vendedor la reserva.
        Long p = libres.get(0).getId();
        assertThat(reserva.reservar(p, 9999L)).isTrue();
        assertThat(puestoDao.findById(p).orElseThrow().getEstadoPuesto()).isEqualTo(Puesto.EN_TRAMITE);
    }
}
