package com.usic.uniFex.model.service;

import java.util.Date;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dao.IPlanoTextoDao;
import com.usic.uniFex.model.dto.PlanoTextoDTO;
import com.usic.uniFex.model.entity.PlanoTexto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Los rotulos libres del plano.
 *
 * <h2>Se difunde siempre</h2>
 * Igual que el estado de una caseta: toda escritura que sale bien se manda por
 * {@link #TOPIC}. Un rotulo que cambia sin difundirse deja a los demas vendedores leyendo
 * "ENTRADA" donde ya no la hay, que es el mismo problema de siempre con otra ropa.
 *
 * <h2>Baja logica</h2>
 * Borrar de verdad no aporta nada y quita la posibilidad de entender que paso. La baja viaja
 * como un DTO con {@code activo=false} y el cliente lo quita de su plano.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanoTextoService {

    public static final String TOPIC = "/topic/plano-textos";

    private final IPlanoTextoDao dao;
    private final IEdicionDao edicionDao;
    private final SimpMessagingTemplate messaging;

    /** Cambios parciales: lo que llega nulo no se toca (semantica PATCH). */
    public record CambioTexto(String contenido, Double mapaX, Double mapaY, Double tamano,
                              String color, String colorBorde, Double grosorBorde, Double rotacion) {
    }

    @Transactional(readOnly = true)
    public List<PlanoTextoDTO> listar() {
        Long edicionId = edicionDao.findFirstByActivaTrueOrderByAnioDesc()
                .map(e -> e.getId()).orElse(null);
        return dao.vivosDe(edicionId).stream().map(PlanoTextoDTO::de).toList();
    }

    @Transactional
    public PlanoTextoDTO crear(CambioTexto req, Long usuarioId) {
        String contenido = limpio(req.contenido());
        if (contenido == null) throw new IllegalArgumentException("El texto no puede estar vacio");

        PlanoTexto t = new PlanoTexto();
        t.setContenido(contenido);
        t.setMapaX(req.mapaX() == null ? 0.5 : acotar(req.mapaX()));
        t.setMapaY(req.mapaY() == null ? 0.5 : acotar(req.mapaY()));
        // Los valores por defecto se ponen AQUI y no solo en el DEFAULT de la tabla: Hibernate
        // incluye la columna en el INSERT con NULL, asi que el DEFAULT de la base no llega a
        // aplicarse nunca. Es una trampa ya pagada con `categoria.tamano_mapa`.
        t.setTamano(req.tamano() == null ? PlanoTexto.TAMANO_POR_DEFECTO : req.tamano());
        t.setColor(req.color() == null ? "#111827" : req.color());
        t.setColorBorde(req.colorBorde() == null ? "#ffffff" : req.colorBorde());
        t.setGrosorBorde(req.grosorBorde() == null ? PlanoTexto.GROSOR_BORDE_POR_DEFECTO : req.grosorBorde());
        t.setRotacion(req.rotacion() == null ? 0d : req.rotacion());
        t.setEdicion(edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null));
        // La auditoria de JPA esta APAGADA en este proyecto: si no se sella a mano, queda nula.
        t.setRegistro(new Date());
        t.setRegistroIdUsuario(usuarioId);
        t.setEstado(PlanoTexto.REGISTRO_ACTIVO);

        PlanoTextoDTO dto = PlanoTextoDTO.de(dao.save(t));
        difundir(dto);
        log.info("Rotulo {} creado en el plano por el usuario {}", dto.id(), usuarioId);
        return dto;
    }

    @Transactional
    public PlanoTextoDTO actualizar(Long id, CambioTexto req, Long usuarioId) {
        PlanoTexto t = dao.findById(id).orElse(null);
        if (t == null || PlanoTexto.REGISTRO_ANULADO.equals(t.getEstado())) return null;
        if (limpio(req.contenido()) != null) t.setContenido(limpio(req.contenido()));
        if (req.mapaX() != null) t.setMapaX(acotar(req.mapaX()));
        if (req.mapaY() != null) t.setMapaY(acotar(req.mapaY()));
        if (req.tamano() != null) t.setTamano(req.tamano());
        if (req.color() != null) t.setColor(req.color());
        if (req.colorBorde() != null) t.setColorBorde(req.colorBorde());
        if (req.grosorBorde() != null) t.setGrosorBorde(req.grosorBorde());
        if (req.rotacion() != null) t.setRotacion(req.rotacion());
        t.setModificacion(new Date());
        t.setModificacionIdUsuario(usuarioId);

        PlanoTextoDTO dto = PlanoTextoDTO.de(dao.save(t));
        difundir(dto);
        return dto;
    }

    /** Baja logica. Devuelve false si no existia. */
    @Transactional
    public boolean eliminar(Long id, Long usuarioId) {
        PlanoTexto t = dao.findById(id).orElse(null);
        if (t == null || PlanoTexto.REGISTRO_ANULADO.equals(t.getEstado())) return false;
        t.setEstado(PlanoTexto.REGISTRO_ANULADO);
        t.setModificacion(new Date());
        t.setModificacionIdUsuario(usuarioId);
        dao.save(t);
        difundir(PlanoTextoDTO.baja(id));
        log.info("Rotulo {} retirado del plano por el usuario {}", id, usuarioId);
        return true;
    }

    /**
     * Manda el rotulo a todos los planos abiertos.
     *
     * Un fallo al difundir NUNCA tumba la escritura que ya se guardo: es la misma regla que
     * sigue el estado de las casetas. El cliente se pondra al dia al recargar.
     */
    private void difundir(PlanoTextoDTO dto) {
        try {
            messaging.convertAndSend(TOPIC, dto);
        } catch (Exception e) {
            log.warn("No se pudo difundir el rotulo {}: {}", dto.id(), e.getMessage());
        }
    }

    /** Dentro del plano: una fraccion fuera de 0..1 pondria el rotulo donde nadie lo ve. */
    private static double acotar(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
