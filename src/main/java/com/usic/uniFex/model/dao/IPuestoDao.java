package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Puesto;

public interface IPuestoDao extends JpaRepository <Puesto, Long>{
    @EntityGraph(attributePaths = {"categoria"})
    List<Puesto> findAll();

    @Query("SELECT p FROM Puesto p WHERE p.estadoPuesto = 'L' ORDER BY p.codigo ASC")
    List<Puesto> listarPuestos();

    @Query("SELECT p FROM Puesto p " +
       "WHERE p.estadoPuesto = :estadoPuesto AND p.categoria.id = :categoriaId " +
       "ORDER BY CAST(p.codigo AS integer) ASC")
List<Puesto> findLibresPorCategoriaOrdenados(@Param("estadoPuesto") String estadoPuesto,
                                             @Param("categoriaId") Long categoriaId);

    // ===== Reserva atomica (Fase 1) =====
    // Cada metodo es un UPDATE condicional: gana solo si la fila esta en el
    // estado esperado. Devuelve el numero de filas afectadas (1 = exito, 0 = perdio).

    /** LIBRE -> EN_TRAMITE. Reserva la caseta por ttlSegundos si sigue libre. */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'T', " +
           "reservado_por_id_usuario = :usuarioId, " +
           "reserva_expira = now() + (:ttlSegundos * interval '1 second'), " +
           "version = version + 1 " +
           "WHERE id = :id AND estado_puesto = 'L'", nativeQuery = true)
    int reservarSiLibre(@Param("id") Long id,
                        @Param("usuarioId") Long usuarioId,
                        @Param("ttlSegundos") long ttlSegundos);

    /**
     * LIBRE -> EN_TRAMITE, o renovar la reserva propia. Es la operacion del "carrito":
     * el vendedor va sumando casetas antes de registrar la venta, y volver a pulsar una que
     * ya es suya no debe fallar ni robarsela a nadie.
     *
     * Gana si la caseta esta libre O si ya esta en tramite a nombre del MISMO usuario; en
     * el segundo caso solo se refresca el vencimiento. Sigue siendo un UPDATE condicional:
     * una caseta en tramite de otro vendedor, ocupada o bloqueada, afecta 0 filas.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'T', " +
           "reservado_por_id_usuario = :usuarioId, " +
           "reserva_expira = now() + (:ttlSegundos * interval '1 second'), " +
           "version = version + 1 " +
           "WHERE id = :id AND (estado_puesto = 'L' " +
           "  OR (estado_puesto = 'T' AND reservado_por_id_usuario = :usuarioId))",
           nativeQuery = true)
    int reservarSiLibreOMia(@Param("id") Long id,
                            @Param("usuarioId") Long usuarioId,
                            @Param("ttlSegundos") long ttlSegundos);

    /** Casetas que este vendedor tiene en tramite ahora mismo: es su carrito. */
    @EntityGraph(attributePaths = {"categoria"})
    @Query("SELECT p FROM Puesto p WHERE p.estadoPuesto = 'T' AND p.reservadoPorIdUsuario = :usuarioId "
         + "ORDER BY p.categoria.nombre ASC, p.id ASC")
    List<Puesto> reservadasPor(@Param("usuarioId") Long usuarioId);

    /** EN_TRAMITE -> LIBRE. Solo el usuario que la reservo puede liberarla. */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'L', " +
           "reservado_por_id_usuario = NULL, reserva_expira = NULL, version = version + 1 " +
           "WHERE id = :id AND estado_puesto = 'T' AND reservado_por_id_usuario = :usuarioId",
           nativeQuery = true)
    int liberarSiEnTramite(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /** EN_TRAMITE -> OCUPADO. Confirma la venta del titular de la reserva. */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'O', " +
           "reserva_expira = NULL, version = version + 1 " +
           "WHERE id = :id AND estado_puesto = 'T' AND reservado_por_id_usuario = :usuarioId",
           nativeQuery = true)
    int confirmarSiEnTramite(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /**
     * Ocupacion directa (LIBRE -> OCUPADO), o confirmacion de una reserva propia
     * (EN_TRAMITE del mismo usuario -> OCUPADO). Es la operacion que usa el registro
     * en un solo paso (/guardar). Actualiza tambien la auditoria de modificacion.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'O', " +
           "reservado_por_id_usuario = NULL, reserva_expira = NULL, version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND (estado_puesto = 'L' " +
           "  OR (estado_puesto = 'T' AND reservado_por_id_usuario = :usuarioId))",
           nativeQuery = true)
    int ocuparSiDisponible(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /** Libera todas las reservas vencidas (T con reserva_expira < now). Devuelve cuantas libero. */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'L', " +
           "reservado_por_id_usuario = NULL, reserva_expira = NULL, version = version + 1 " +
           "WHERE estado_puesto = 'T' AND reserva_expira < now()", nativeQuery = true)
    int liberarReservasVencidas();

    /** Ids de las casetas con reserva vencida (para difundir su liberacion). */
    @Query(value = "SELECT id FROM puesto WHERE estado_puesto = 'T' AND reserva_expira < now()",
           nativeQuery = true)
    List<Long> idsReservasVencidas();

    /**
     * OCUPADO -> LIBRE. Vuelve a la venta una caseta de una inscripcion cancelada.
     *
     * UPDATE condicional como toda transicion: si la caseta ya no esta ocupada
     * (p. ej. se libero antes por error), afecta 0 filas y se queda como esta.
     * No libera casetas en tramite ni bloqueadas: esas no las toca una cancelacion.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'L', " +
           "reservado_por_id_usuario = NULL, reserva_expira = NULL, version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND estado_puesto = 'O'", nativeQuery = true)
    int liberarSiOcupado(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    // ===== Mapa y editor (Fase 3) =====

    /** Casetas no anuladas. Es la lista que ven el mapa y el tablero. */
    @EntityGraph(attributePaths = {"categoria"})
    @Query("SELECT p FROM Puesto p WHERE p.estado IS NULL OR p.estado <> 'X'")
    List<Puesto> listarActivos();

    /**
     * Guarda posicion (0..1) y, si viene, la escala propia. Pasar x/y nulos quita la caseta
     * del plano sin borrarla. Nunca toca una caseta anulada.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET mapa_x = :x, mapa_y = :y, " +
           "mapa_escala = COALESCE(:escala, mapa_escala) " +
           "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarPosicion(@Param("id") Long id, @Param("x") Double x, @Param("y") Double y,
                           @Param("escala") Double escala);

    /**
     * Anula una caseta (baja logica). UPDATE condicional, como toda transicion:
     * solo si esta LIBRE, no esta ya anulada y NO tiene ninguna venta ligada.
     *
     * No se borra la fila porque inscripcion_puesto la referencia: hay casetas anuladas
     * que conservan ventas historicas. Ademas se pone estado_puesto='X' para que el sitio
     * Thymeleaf viejo deje de ofrecerla (fn_lista_puestos filtra por ese campo, no por _estado),
     * y se la quita del plano.
     *
     * @return 1 si la anulo, 0 si no cumplia las condiciones.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET \"_estado\" = 'X', estado_puesto = 'X', " +
           "mapa_x = NULL, mapa_y = NULL, version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND estado_puesto = 'L' " +
           "  AND (\"_estado\" IS NULL OR \"_estado\" <> 'X') " +
           "  AND NOT EXISTS (SELECT 1 FROM inscripcion_puesto ip WHERE ip.id_puesto = puesto.id)",
           nativeQuery = true)
    int anularSiLibreYSinVentas(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /**
     * Bloquea una caseta libre (LIBRE -> BLOQUEADO, reparacion). Es un bloqueo real,
     * distinto de la baja logica: solo cambia estado_puesto, NO toca _estado, asi que
     * la caseta sigue activa y se ve gris (X) en el mapa. Un bloqueado no se puede
     * reservar, liberar ni confirmar.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'X', version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND estado_puesto = 'L' " +
           "  AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int bloquearSiLibre(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /**
     * Desbloquea (BLOQUEADO -> LIBRE). Solo reactiva un bloqueo real: la condicion
     * (_estado <> 'X') garantiza que nunca vuelve a la venta una caseta ANULADA.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET estado_puesto = 'L', version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id = :id AND estado_puesto = 'X' " +
           "  AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int desbloquearSiBloqueada(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /**
     * Guarda la referencia en texto de donde esta la caseta ("frente a la puerta 3").
     * Como toda escritura sobre puesto, es condicional: nunca toca una caseta anulada.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET referencia = :referencia, "
           + "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId "
           + "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarReferencia(@Param("id") Long id,
                             @Param("referencia") String referencia,
                             @Param("usuarioId") Long usuarioId);

    /** Ids de las casetas activas de una categoria (para difundir un cambio de color/forma/tamaño). */
    @Query("SELECT p.id FROM Puesto p WHERE p.categoria.id = :categoriaId " +
           "AND (p.estado IS NULL OR p.estado <> 'X')")
    List<Long> idsActivosDeCategoria(@Param("categoriaId") Long categoriaId);

    /** Casetas activas de una categoria (para ajustar la cantidad o eliminar la categoria). */
    @Query("SELECT p FROM Puesto p WHERE p.categoria.id = :categoriaId " +
           "AND (p.estado IS NULL OR p.estado <> 'X')")
    List<Puesto> activosDeCategoria(@Param("categoriaId") Long categoriaId);

    /**
     * Cuenta las casetas de la categoria que NO se pueden dar de baja: ocupadas, en tramite,
     * o con ventas historicas. Si es > 0, la categoria no se puede eliminar sin perder datos.
     */
    @Query(value = "SELECT count(*) FROM puesto p WHERE p.id_categoria = :categoriaId " +
           "AND (p.\"_estado\" IS NULL OR p.\"_estado\" <> 'X') " +
           "AND (p.estado_puesto <> 'L' " +
           "     OR EXISTS (SELECT 1 FROM inscripcion_puesto ip WHERE ip.id_puesto = p.id))",
           nativeQuery = true)
    long contarNoEliminablesDeCategoria(@Param("categoriaId") Long categoriaId);

    /**
     * Anula en bloque todas las casetas activas de una categoria. Solo debe llamarse tras
     * comprobar con {@link #contarNoEliminablesDeCategoria} que ninguna tiene ventas: este
     * UPDATE no vuelve a mirarlo, lo hace la capa de servicio antes de invocarlo.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET \"_estado\" = 'X', estado_puesto = 'X', " +
           "mapa_x = NULL, mapa_y = NULL, version = version + 1, " +
           "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId " +
           "WHERE id_categoria = :categoriaId AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')",
           nativeQuery = true)
    int anularCasetasDeCategoria(@Param("categoriaId") Long categoriaId, @Param("usuarioId") Long usuarioId);
}
