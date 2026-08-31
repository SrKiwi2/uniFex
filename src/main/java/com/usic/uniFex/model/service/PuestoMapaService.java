package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Colocacion, redimension, alta y baja de casetas sobre el plano (Fase 3, editor). */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuestoMapaService {

    private final IPuestoDao puestoDao;
    private final ICategoriaService categoriaService;

    /**
     * Posicion normalizada (0..1) de una caseta, con su escala opcional.
     * {@code x} e {@code y} nulos significan "quitar del plano" (la caseta sigue existiendo).
     * {@code escala} nula significa "no la cambies".
     */
    public record Posicion(Long id, Double x, Double y, Double escala) {
    }

    /**
     * Guarda un lote de posiciones.
     * @return los ids realmente actualizados, para difundirlos por WebSocket.
     */
    @Transactional
    public List<Long> guardarPosiciones(List<Posicion> posiciones) {
        List<Long> cambiados = new ArrayList<>();
        for (Posicion p : posiciones) {
            if (p.id() == null) continue;
            if (puestoDao.actualizarPosicion(p.id(), p.x(), p.y(), p.escala()) > 0) {
                cambiados.add(p.id());
            }
        }
        log.info("Editor: {} casetas reposicionadas de {} enviadas", cambiados.size(), posiciones.size());
        return cambiados;
    }

    /**
     * Da de baja logica una caseta. Solo si esta libre y no arrastra ventas: la condicion
     * viaja en el WHERE del UPDATE, no en un if.
     * @return true si se anulo; false si estaba vendida, reservada o ya anulada.
     */
    @Transactional
    public boolean anular(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.anularSiLibreYSinVentas(puestoId, usuarioId) > 0;
        log.info("Anulacion puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    /**
     * Bloquea una caseta libre (estado X por reparacion). No es una anulacion: solo cambia
     * estado_puesto, la caseta sigue activa y se ve gris en el mapa sin poder venderse.
     * @return true si se bloqueo; false si no estaba libre.
     */
    @Transactional
    public boolean bloquear(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.bloquearSiLibre(puestoId, usuarioId) > 0;
        log.info("Bloqueo puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    /**
     * Guarda la referencia en texto de donde esta la caseta ("frente a la puerta 3").
     *
     * Complementa al plano: el mapa dice donde esta, esto lo dice con palabras que el cliente
     * entiende por telefono. Un texto vacio se guarda como null, para que "sin referencia" sea
     * un unico valor y no dos.
     * @return true si se guardo; false si la caseta no existe o esta anulada.
     */
    @Transactional
    public boolean cambiarReferencia(Long puestoId, String referencia, Long usuarioId) {
        String limpia = (referencia == null || referencia.isBlank()) ? null : referencia.trim();
        return puestoDao.actualizarReferencia(puestoId, limpia, usuarioId) > 0;
    }

    /**
     * Desbloquea una caseta bloqueada (X -> L). Nunca reactiva una anulada: la condicion
     * viaja en el WHERE del UPDATE.
     * @return true si se desbloqueo; false si no estaba bloqueada o estaba anulada.
     */
    @Transactional
    public boolean desbloquear(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.desbloquearSiBloqueada(puestoId, usuarioId) > 0;
        log.info("Desbloqueo puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    /** Alta de una caseta suelta dentro de una categoria. @return la caseta creada, o null si la categoria no existe. */
    @Transactional
    public Puesto crear(Long categoriaId, String codigo, String tamano, Long usuarioId) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) return null;

        Date ahora = new Date();
        Puesto p = new Puesto();
        p.setCodigo(codigo);
        p.setTamano(tamano);
        p.setEstadoPuesto(Puesto.LIBRE);
        p.setCategoria(c);
        p.setMapaEscala(1.0);
        // La auditoria de JPA esta apagada: estos campos se ponen a mano o quedan nulos.
        p.setEstado(Puesto.REGISTRO_ACTIVO);
        p.setRegistro(ahora);
        p.setModificacion(ahora);
        p.setRegistroIdUsuario(usuarioId);
        p.setModificacionIdUsuario(usuarioId);
        return puestoDao.save(p);
    }
}
