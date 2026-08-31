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
import com.usic.uniFex.model.dto.SolicitudCancelacionDTO;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.RegistroVentaService;
import com.usic.uniFex.model.service.SolicitudCancelacionService;

/**
 * Flujo de solicitud de cancelacion (V11), contra la copia LOCAL (perfil dev).
 *
 * Lo que se protege: el vendedor solo puede pedir con motivo y una sola solicitud
 * pendiente a la vez; administracion decide (aprobar o rechazar, con respuesta
 * obligatoria al rechazar) y cada decision deja huella de auditoria. El estado de
 * la solicitud se ve en la cola del admin y en "mis solicitudes" del vendedor.
 *
 * Ejecutar:  mvnw.cmd test "-Dtest=SolicitudCancelacionTest"
 */
@SpringBootTest
@ActiveProfiles("dev")
class SolicitudCancelacionTest {

    private static final String NOMBRE_PRUEBA = "ENTIDAD DE PRUEBA SOLICITUD";

    @Autowired private RegistroVentaService registro;
    @Autowired private SolicitudCancelacionService solicitudes;
    @Autowired private IPuestoDao puestoDao;
    @Autowired private IInscripcionDao inscripcionDao;
    @Autowired private JdbcTemplate jdbc;

    private List<Puesto> libres;
    private Long categoriaId;
    private BigDecimal precioOriginal;

    @BeforeEach
    void setUp() {
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

    private void limpiarDatosDePrueba() {
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

    private Long venderUnaVenta() {
        RegistroVentaService.NuevaVenta venta = new RegistroVentaService.NuevaVenta(
                NOMBRE_PRUEBA, "123456789", "Descripcion", "Objeto",
                "Representante Legal", "99887766",
                jdbc.queryForObject("SELECT id FROM tipo_entidad ORDER BY id LIMIT 1", Long.class),
                LocalDate.now(), LocalDate.now().plusDays(5),
                List.of(new RegistroVentaService.DatosPersona("TITULAR", "Perez", "Lopez", "111", "t@x.com", "700")),
                null, null, false,
                libres.stream().map(Puesto::getId).toList());
        RegistroVentaService.Resultado r = registro.registrar(venta, 1L);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        return r.inscripcionId();
    }

    private int auditoria(String accion, Long inscripcionId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE tabla='inscripcion' "
                        + "AND id_registro=? AND accion=?",
                Integer.class, inscripcionId, accion);
    }

    private SolicitudCancelacionDTO pendienteDe(Long inscripcionId) {
        return solicitudes.pendientes().stream()
                .filter(s -> s.inscripcionId().equals(inscripcionId))
                .findFirst().orElse(null);
    }

    @Test
    void elVendedorSolicitaConMotivoYQuedaPendiente() {
        Long id = venderUnaVenta();

        SolicitudCancelacionService.Resultado r =
                solicitudes.solicitar(id, "El cliente desistio del pago", 1L, "WEB");

        assertThat(r.ok()).as(r.mensaje()).isTrue();

        // Aparece en la cola del admin, con la venta y quien la pidio.
        SolicitudCancelacionDTO s = pendienteDe(id);
        assertThat(s).isNotNull();
        assertThat(s.estado()).isEqualTo("PENDIENTE");
        assertThat(s.motivo()).isEqualTo("El cliente desistio del pago");
        assertThat(s.vendedor()).isNotBlank();
        assertThat(s.entidad()).isEqualTo(NOMBRE_PRUEBA);

        // Y en "mis solicitudes" del vendedor.
        assertThat(solicitudes.delVendedor(1L)).anyMatch(x -> x.id().equals(s.id()));

        // Huella: registro + solicitud.
        assertThat(auditoria("SOLICITUD_CANCELACION", id)).isEqualTo(1);
    }

    @Test
    void sinMotivoNoSolicita() {
        Long id = venderUnaVenta();

        SolicitudCancelacionService.Resultado r = solicitudes.solicitar(id, "   ", 1L, "WEB");

        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("obligatorio");
        assertThat(solicitudes.pendientes()).isEmpty();
        assertThat(auditoria("SOLICITUD_CANCELACION", id)).isZero();
    }

    @Test
    void unaSolaSolicitudPendientePorVenta() {
        Long id = venderUnaVenta();
        assertThat(solicitudes.solicitar(id, "Primera", 1L, "WEB").ok()).isTrue();

        SolicitudCancelacionService.Resultado r = solicitudes.solicitar(id, "Segunda", 1L, "WEB");

        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("en espera");
        assertThat(solicitudes.pendientes()).hasSize(1);
    }

    @Test
    void elRechazoExigeRespuestaYSePuedeVolverASolicitar() {
        Long id = venderUnaVenta();
        Long solicitudId = solicitudes.solicitar(id, "Quiero cancelar", 1L, "WEB").solicitudId();

        // Rechazo sin respuesta: no procede.
        assertThat(solicitudes.rechazar(solicitudId, "  ", 2L, "WEB").ok()).isFalse();

        // Rechazo con respuesta: la solicitud queda rechazada con el motivo del admin.
        assertThat(solicitudes.rechazar(solicitudId, "El comprobante no se pudo verificar", 2L, "WEB").ok()).isTrue();
        SolicitudCancelacionDTO s = solicitudes.resueltas().stream()
                .filter(x -> x.id().equals(solicitudId)).findFirst().orElseThrow();
        assertThat(s.estado()).isEqualTo("RECHAZADA");
        assertThat(s.respuesta()).isEqualTo("El comprobante no se pudo verificar");
        assertThat(s.resueltoPor()).isNotBlank();
        assertThat(auditoria("RECHAZO_CANCELACION", id)).isEqualTo(1);

        // Rechazar dos veces: la segunda ya no pilla una pendiente.
        assertThat(solicitudes.rechazar(solicitudId, "Otra vez", 2L, "WEB").ok()).isFalse();

        // Tras el rechazo el vendedor puede volver a pedir (la cola vuelve a tener una).
        assertThat(solicitudes.solicitar(id, "Reintento", 1L, "WEB").ok()).isTrue();
        assertThat(pendienteDe(id)).isNotNull();
    }

    @Test
    void alAprobarElVendedorQuedaHabilitadoYSaleDeLaCola() {
        Long id = venderUnaVenta();
        Long solicitudId = solicitudes.solicitar(id, "Desistio", 1L, "WEB").solicitudId();

        assertThat(solicitudes.aprobar(solicitudId, 2L, "WEB").ok()).isTrue();

        // Sale de la cola y entra en resueltas como APROBADA.
        assertThat(pendienteDe(id)).isNull();
        SolicitudCancelacionDTO s = solicitudes.resueltas().stream()
                .filter(x -> x.id().equals(solicitudId)).findFirst().orElseThrow();
        assertThat(s.estado()).isEqualTo("APROBADA");
        assertThat(auditoria("APROBACION_CANCELACION", id)).isEqualTo(1);

        // Aprobar dos veces: la segunda no pilla una pendiente.
        assertThat(solicitudes.aprobar(solicitudId, 2L, "WEB").ok()).isFalse();

        // La venta sigue intacta hasta que alguien ejecute la cancelacion.
        assertThat(inscripcionDao.findById(id).orElseThrow().getEstado()).isNotEqualTo("X");
        for (Puesto p : libres) {
            assertThat(puestoDao.findById(p.getId()).orElseThrow().getEstadoPuesto())
                    .isEqualTo(Puesto.OCUPADO);
        }
    }

    @Test
    void elEstadoDeVentaReflejaLoQueVeElVendedor() {
        Long id = venderUnaVenta();

        // Nunca se solicito: null.
        assertThat(solicitudes.estadoDeVenta(id)).isNull();

        // Pendiente: en espera.
        solicitudes.solicitar(id, "Desistio", 1L, "WEB");
        assertThat(solicitudes.estadoDeVenta(id).estado()).isEqualTo("PENDIENTE");

        // Aprobada: habilitado para cancelar.
        Long solicitudId = solicitudes.pendientes().stream()
                .filter(s -> s.inscripcionId().equals(id)).findFirst().orElseThrow().id();
        solicitudes.aprobar(solicitudId, 2L, "WEB");
        assertThat(solicitudes.estadoDeVenta(id).estado()).isEqualTo("APROBADA");
    }
}
