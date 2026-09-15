package com.usic.uniFex.model.service;

import java.util.Collection;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dto.PuestoEstadoDTO;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Difunde el estado actual de una caseta a todos los clientes suscritos al
 * topic {@code /topic/puestos}. Es el punto unico de publicacion en tiempo real.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PuestoEventPublisher {

    public static final String TOPIC = "/topic/puestos";

    /**
     * Topic de los cambios de asignacion: que caseta pasa a llevar quien.
     *
     * Va aparte de {@link #TOPIC} porque son dos cosas con ritmos muy distintos. El estado de
     * una caseta cambia en cada venta; quien la lleva, casi nunca. Meter el nombre y el
     * telefono del vendedor en PuestoEstadoDTO habria engordado TODOS los mensajes de venta
     * —los que mas viajan— para transportar algo que se toca una vez al mes.
     */
    public static final String TOPIC_ASIGNACIONES = "/topic/asignaciones";

    private final SimpMessagingTemplate messaging;
    private final IPuestoDao puestoDao;

    /**
     * Publica el estado vigente del puesto.
     *
     * Se lee con la categoria YA CARGADA (`findWithCategoriaByIdIn`) y no con `findById`: la
     * categoria es lazy y el DTO le pide nombre, color, forma, tamaño y precio, asi que un
     * `findById` son dos viajes a la base en vez de uno. Lo paga el mapa de los demas
     * vendedores, que no ve el cambio hasta que esto termina.
     */
    @Transactional(readOnly = true)
    public void publicar(Long puestoId) {
        if (puestoId == null) return;
        publicarVarios(java.util.List.of(puestoId));
    }

    /**
     * Difunde los cambios de asignacion: solo las casetas que cambiaron de dueño.
     *
     * Se manda el DELTA y no la tabla entera a proposito: en la feria la red es mala y
     * reasignar tres casetas no puede costar la lista completa a cada movil conectado. Una
     * caseta que se queda sin vendedor viaja con {@code vendedorId} nulo, que es como el
     * cliente sabe que tiene que pintarla de gris y olvidar el contacto.
     */
    public void publicarAsignaciones(java.util.List<com.usic.uniFex.model.dto.AsignacionPuestoDTO> cambios) {
        if (cambios == null || cambios.isEmpty()) return;
        try {
            messaging.convertAndSend(TOPIC_ASIGNACIONES, cambios);
            log.info("Difundidos {} cambios de asignacion", cambios.size());
        } catch (Exception e) {
            // Igual que el estado de las casetas: un fallo al difundir NUNCA puede tumbar la
            // operacion que ya se guardo. El cliente se enterara al resincronizar.
            log.error("No se pudieron difundir los cambios de asignacion", e);
        }
    }

    /**
     * Publica varias casetas de una vez. Lo usan el carrito, el guardado por lotes del editor y
     * los cambios de categoria (color, forma, tamaño), que alteran todas sus casetas.
     *
     * Se emite un mensaje por caseta y no un lote unico para no cambiar el contrato que ya
     * consumen el mapa y la app: cada mensaje sigue siendo un PuestoEstadoDTO.
     *
     * Pero se LEE todo de una vez. Antes esto era un bucle de `findById`, y con la categoria
     * lazy cada caseta costaba dos viajes a la base: veinte casetas eran cuarenta viajes, y
     * hasta que terminaban no salia ni el primer mensaje. Es el tramo que decide cuanto tarda
     * el mapa del otro vendedor en enterarse, y el unico que crece con el tamaño del lote.
     *
     * Los mensajes se mandan segun se van armando, no al final: el primero sale sin esperar a
     * que se serialice el ultimo.
     */
    @Transactional(readOnly = true)
    public void publicarVarios(Collection<Long> puestoIds) {
        if (puestoIds == null || puestoIds.isEmpty()) return;
        try {
            List<Puesto> casetas = puestoDao.findWithCategoriaByIdIn(puestoIds);
            for (Puesto p : casetas) {
                messaging.convertAndSend(TOPIC, PuestoEstadoDTO.de(p));
            }
            log.info("Difundidas {} casetas de {} pedidas", casetas.size(), puestoIds.size());
        } catch (Exception e) {
            // Un fallo al difundir NUNCA puede tumbar la operacion que ya se guardo: la venta
            // esta hecha. El cliente se pondra al dia en la siguiente resincronizacion.
            // La traza entera, no solo el mensaje: si esto falla, el mapa de todos se queda
            // viejo y `e.getMessage()` a secas no dice en que linea se rompio.
            log.error("No se pudo difundir el estado de {} caseta(s)", puestoIds.size(), e);
        }
    }
}
