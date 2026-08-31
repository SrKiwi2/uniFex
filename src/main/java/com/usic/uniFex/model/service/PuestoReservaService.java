package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Motor de reserva atomica de puestos (Fase 1) — evita la doble venta.
 *
 * La garantia no viene del codigo Java sino de un UPDATE condicional en la BD:
 * {@code ... WHERE id = ? AND estado_puesto = 'L'}. Si dos usuarios intentan
 * reservar la misma caseta a la vez, PostgreSQL serializa el acceso a la fila y
 * solo el primero la ve como 'L'; el segundo afecta 0 filas y recibe "no disponible".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuestoReservaService {

    private final IPuestoDao puestoDao;

    /** Segundos que dura la reserva temporal antes de liberarse sola. */
    @Value("${unifex.reserva.ttl-segundos:300}")
    private long ttlSegundos;

    /**
     * Segundos que dura una caseta en el carrito (por defecto 12 h).
     *
     * Es mucho mas largo que la reserva suelta a proposito: el vendedor tiene que poder
     * elegir varias casetas, cerrar el movil, hablar con el cliente y volver a terminar el
     * registro sin que se le liberen por el camino. Sigue habiendo limite para que una venta
     * abandonada no bloquee casetas para siempre; el barrido de siempre las recupera.
     */
    @Value("${unifex.reserva.carrito-ttl-segundos:43200}")
    private long carritoTtlSegundos;

    /**
     * Intenta reservar (LIBRE -> EN_TRAMITE) la caseta para el usuario.
     * @return true si la reservo; false si otra persona ya la tomo o no estaba libre.
     */
    @Transactional
    public boolean reservar(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.reservarSiLibre(puestoId, usuarioId, ttlSegundos) > 0;
        log.info("Reserva puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "NO DISPONIBLE");
        return ok;
    }

    /** Resultado de una operacion en lote: que casetas se lograron y cuales no. */
    public record ResultadoLote(List<Long> logradas, List<Long> rechazadas) {
        public boolean todoOk() {
            return rechazadas.isEmpty();
        }
    }

    /**
     * Suma varias casetas al carrito del vendedor, con el vencimiento largo.
     *
     * Cada caseta se resuelve por separado con su propio UPDATE condicional: si una la gano
     * otro vendedor mientras tanto, esa se rechaza y **las demas siguen**. Anular el lote
     * entero por una caseta perdida seria peor para el vendedor, que tendria que rehacer la
     * seleccion completa.
     */
    @Transactional
    public ResultadoLote agregarAlCarrito(List<Long> puestoIds, Long usuarioId) {
        List<Long> ok = new ArrayList<>();
        List<Long> no = new ArrayList<>();
        for (Long id : puestoIds) {
            if (puestoDao.reservarSiLibreOMia(id, usuarioId, carritoTtlSegundos) > 0) ok.add(id);
            else no.add(id);
        }
        log.info("Carrito usuario={} agregadas={} rechazadas={}", usuarioId, ok.size(), no.size());
        return new ResultadoLote(ok, no);
    }

    /** Quita varias casetas del carrito. Solo suelta las del propio vendedor. */
    @Transactional
    public ResultadoLote quitarDelCarrito(List<Long> puestoIds, Long usuarioId) {
        List<Long> ok = new ArrayList<>();
        List<Long> no = new ArrayList<>();
        for (Long id : puestoIds) {
            if (puestoDao.liberarSiEnTramite(id, usuarioId) > 0) ok.add(id);
            else no.add(id);
        }
        return new ResultadoLote(ok, no);
    }

    /** Las casetas que el vendedor tiene ahora mismo en tramite. */
    @Transactional(readOnly = true)
    public List<Puesto> carritoDe(Long usuarioId) {
        return puestoDao.reservadasPor(usuarioId);
    }

    /** Libera la reserva propia (EN_TRAMITE -> LIBRE). @return true si se libero. */
    @Transactional
    public boolean liberar(Long puestoId, Long usuarioId) {
        return puestoDao.liberarSiEnTramite(puestoId, usuarioId) > 0;
    }

    /** Confirma la venta del titular de la reserva (EN_TRAMITE -> OCUPADO). @return true si se confirmo. */
    @Transactional
    public boolean confirmar(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.confirmarSiEnTramite(puestoId, usuarioId) > 0;
        log.info("Confirmacion puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    /**
     * Ocupa la caseta en un solo paso (LIBRE -> OCUPADO), o confirma la reserva
     * propia. Lo usa el registro directo (/guardar). Si se llama dentro de una
     * transaccion existente se une a ella (propagacion por defecto), de modo que
     * un fallo posterior revierte tambien esta ocupacion.
     * @return true si quedo ocupada; false si la caseta ya no estaba disponible.
     */
    @Transactional
    public boolean ocupar(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.ocuparSiDisponible(puestoId, usuarioId) > 0;
        log.info("Ocupacion puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "NO DISPONIBLE");
        return ok;
    }

    /** Libera todas las reservas vencidas. @return ids de las casetas liberadas. */
    @Transactional
    public List<Long> liberarVencidas() {
        List<Long> ids = puestoDao.idsReservasVencidas();
        if (!ids.isEmpty()) {
            puestoDao.liberarReservasVencidas();
            log.info("Reservas vencidas liberadas: {}", ids.size());
        }
        return ids;
    }
}
