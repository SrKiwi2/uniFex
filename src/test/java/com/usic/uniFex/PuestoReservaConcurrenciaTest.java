package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.PuestoMapaService;
import com.usic.uniFex.model.service.PuestoReservaService;

/**
 * Prueba del nucleo anti doble-venta (Fase 1).
 *
 * Corre contra la copia LOCAL de la BD (perfil dev). Toma una caseta libre,
 * lanza muchos hilos que intentan reservarla en el mismo instante y verifica
 * que EXACTAMENTE UNO gana. Deja la caseta como estaba (LIBRE) al terminar.
 *
 * Ejecutar:  mvnw.cmd test "-Dtest=PuestoReservaConcurrenciaTest"
 */
@SpringBootTest
@ActiveProfiles("dev")
class PuestoReservaConcurrenciaTest {

    @Autowired private PuestoReservaService reserva;
    @Autowired private PuestoMapaService mapa;
    @Autowired private IPuestoDao puestoDao;
    @Autowired private JdbcTemplate jdbc;

    private Long puestoId;

    @BeforeEach
    void setUp() {
        Puesto libre = puestoDao.listarPuestos().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay ninguna caseta LIBRE para la prueba"));
        puestoId = libre.getId();
        resetLibre();
    }

    @AfterEach
    void tearDown() {
        resetLibre();
    }

    private void resetLibre() {
        jdbc.update("UPDATE puesto SET estado_puesto='L', reservado_por_id_usuario=NULL, "
                + "reserva_expira=NULL WHERE id=?", puestoId);
    }

    @Test
    void soloUnUsuarioGanaLaCasetaBajoConcurrencia() throws Exception {
        final int hilos = 25;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos); // todos preparados
        CountDownLatch disparo = new CountDownLatch(1);     // arranque simultaneo
        AtomicInteger exitos = new AtomicInteger(0);

        List<Future<Boolean>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            final long usuarioId = 1000L + i;
            Callable<Boolean> tarea = () -> {
                listos.countDown();
                disparo.await();
                boolean gano = reserva.reservar(puestoId, usuarioId);
                if (gano) exitos.incrementAndGet();
                return gano;
            };
            futuros.add(pool.submit(tarea));
        }

        listos.await();      // esperar a que los 25 hilos esten listos
        disparo.countDown(); // ...y lanzarlos a la vez
        for (Future<Boolean> f : futuros) f.get();
        pool.shutdown();

        // Exactamente un hilo pudo reservar la caseta.
        assertThat(exitos.get())
                .as("solo un usuario debe ganar la caseta")
                .isEqualTo(1);

        // Y la caseta quedo EN_TRAMITE con un titular.
        Puesto p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.EN_TRAMITE);
        assertThat(p.getReservadoPorIdUsuario()).isNotNull();
        assertThat(p.getReservaExpira()).isNotNull();
    }

    @Test
    void bloqueoSoloDesdeLibreYDesbloqueoSoloDesdeBloqueada() {
        // Bloquear una libre: queda BLOQUEADA y fuera de la venta.
        assertThat(mapa.bloquear(puestoId, 3000L)).isTrue();
        Puesto p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.BLOQUEADO);

        // Una bloqueada no se puede reservar: sigue activa pero no se vende.
        assertThat(reserva.reservar(puestoId, 3001L)).isFalse();

        // Re-bloquear una ya bloqueada es rechazado (no hay doble bloqueo).
        assertThat(mapa.bloquear(puestoId, 3000L)).isFalse();

        // Desbloquear: vuelve a LIBRE y a la venta.
        assertThat(mapa.desbloquear(puestoId, 3000L)).isTrue();
        p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.LIBRE);
        assertThat(reserva.reservar(puestoId, 3001L)).isTrue();
        reserva.liberar(puestoId, 3001L);

        // Desbloquear una libre tambien es rechazado (solo reactiva un bloqueo real).
        assertThat(mapa.desbloquear(puestoId, 3000L)).isFalse();
    }

    @Test
    void elBarridoLiberaLaReservaVencida() {
        // Reservar la caseta y luego forzar que su reserva este vencida.
        assertThat(reserva.reservar(puestoId, 5000L)).isTrue();
        jdbc.update("UPDATE puesto SET reserva_expira = now() - interval '1 minute' WHERE id=?", puestoId);

        List<Long> liberadas = reserva.liberarVencidas();

        assertThat(liberadas).as("debe liberar la caseta vencida").contains(puestoId);
        Puesto p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.LIBRE);
        assertThat(p.getReservadoPorIdUsuario()).isNull();
        assertThat(p.getReservaExpira()).isNull();
    }

    @Test
    void elCarritoNoLeRobaCasetasAOtroVendedor() {
        final long vendedorA = 6000L;
        final long vendedorB = 6001L;

        // A la mete en su carrito.
        PuestoReservaService.ResultadoLote r1 = reserva.agregarAlCarrito(List.of(puestoId), vendedorA);
        assertThat(r1.logradas()).containsExactly(puestoId);
        assertThat(r1.todoOk()).isTrue();

        // B intenta meterla en el suyo: rechazada, y sigue siendo de A.
        PuestoReservaService.ResultadoLote r2 = reserva.agregarAlCarrito(List.of(puestoId), vendedorB);
        assertThat(r2.logradas()).isEmpty();
        assertThat(r2.rechazadas()).containsExactly(puestoId);
        assertThat(puestoDao.findById(puestoId).orElseThrow().getReservadoPorIdUsuario())
                .isEqualTo(vendedorA);

        // B tampoco puede sacarla del carrito de A.
        assertThat(reserva.quitarDelCarrito(List.of(puestoId), vendedorB).logradas()).isEmpty();
        assertThat(puestoDao.findById(puestoId).orElseThrow().getEstadoPuesto())
                .isEqualTo(Puesto.EN_TRAMITE);

        // Volver a agregarla estando ya en su carrito es idempotente para A: no falla,
        // solo refresca el vencimiento (el vendedor puede volver a pulsarla sin castigo).
        assertThat(reserva.agregarAlCarrito(List.of(puestoId), vendedorA).todoOk()).isTrue();

        // Y A si puede vaciarla.
        assertThat(reserva.quitarDelCarrito(List.of(puestoId), vendedorA).logradas())
                .containsExactly(puestoId);
        assertThat(puestoDao.findById(puestoId).orElseThrow().getEstadoPuesto())
                .isEqualTo(Puesto.LIBRE);
    }

    @Test
    void elCarritoConservaLoQueSiConsiguioAunqueUnaCasetaSePierda() {
        // Escenario real: el vendedor elige varias y una ya la tomo otro.
        Long ajena = puestoDao.listarPuestos().stream()
                .map(Puesto::getId)
                .filter(id -> !id.equals(puestoId))
                .findFirst()
                .orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(ajena != null,
                "hacen falta al menos dos casetas libres");

        final long otro = 7000L;
        final long vendedor = 7001L;
        assertThat(reserva.agregarAlCarrito(List.of(ajena), otro).todoOk()).isTrue();
        try {
            PuestoReservaService.ResultadoLote r =
                    reserva.agregarAlCarrito(List.of(puestoId, ajena), vendedor);

            // La perdida se informa, pero la que si estaba libre se queda en el carrito:
            // anular el lote entero obligaria al vendedor a rehacer la seleccion.
            assertThat(r.todoOk()).isFalse();
            assertThat(r.logradas()).containsExactly(puestoId);
            assertThat(r.rechazadas()).containsExactly(ajena);
        } finally {
            reserva.quitarDelCarrito(List.of(ajena), otro);
            reserva.quitarDelCarrito(List.of(puestoId), vendedor);
        }
    }

    @Test
    void soloUnUsuarioOcupaLaCasetaBajoConcurrencia() throws Exception {
        // Mismo escenario de carrera pero con ocupacion directa (la que usa /guardar).
        final int hilos = 25;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch disparo = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger(0);

        List<Future<Boolean>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            final long usuarioId = 2000L + i;
            futuros.add(pool.submit(() -> {
                listos.countDown();
                disparo.await();
                boolean ok = reserva.ocupar(puestoId, usuarioId);
                if (ok) exitos.incrementAndGet();
                return ok;
            }));
        }
        listos.await();
        disparo.countDown();
        for (Future<Boolean> f : futuros) f.get();
        pool.shutdown();

        assertThat(exitos.get()).as("solo un registro debe ocupar la caseta").isEqualTo(1);
        Puesto p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.OCUPADO);
    }
}
