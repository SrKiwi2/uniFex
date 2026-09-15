package com.usic.uniFex.model.dao;

import java.util.Collection;
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
     * Guarda posicion (0..1) y, si vienen, la escala y el giro propios. Pasar x/y nulos quita
     * la caseta del plano sin borrarla. Nunca toca una caseta anulada.
     *
     * El giro viaja con la posicion y no por su propia ruta porque el editor las mueve, escala
     * y gira en el mismo gesto y las guarda en el mismo lote: separarlas serian dos escrituras
     * por caseta y dos difusiones por WebSocket para un solo cambio.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET mapa_x = :x, mapa_y = :y, " +
           "mapa_escala = COALESCE(:escala, mapa_escala), " +
           "mapa_rotacion = COALESCE(:rotacion, mapa_rotacion) " +
           "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarPosicion(@Param("id") Long id, @Param("x") Double x, @Param("y") Double y,
                           @Param("escala") Double escala, @Param("rotacion") Integer rotacion);

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

    /**
     * Cambia el numero (codigo) que rotula la caseta en el plano.
     *
     * Como toda escritura sobre puesto es condicional: nunca toca una caseta anulada. No
     * comprueba aqui que el codigo no se repita — eso depende del LOTE entero, porque
     * renumerar es una permutacion (la 3 pasa a ser la 5 y la 5 a ser la 3) y fila a fila
     * cualquier orden de aplicacion pasaria por un estado repetido. La unicidad la valida
     * PuestoMapaService.renumerar antes de escribir nada.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET codigo = :codigo, "
           + "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId "
           + "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarCodigo(@Param("id") Long id,
                         @Param("codigo") String codigo,
                         @Param("usuarioId") Long usuarioId);

    /**
     * Pone (o quita) el precio propio de una caseta (V37).
     *
     * Un {@code null} en {@code precio} NO es un vacio que haya que ignorar: es la orden de
     * devolver la caseta al precio de su categoria. Por eso este UPDATE escribe el valor tal
     * cual y no lleva COALESCE — con COALESCE, "quitarle el precio especial" no haria nada y
     * la caseta se quedaria con el importe viejo para siempre.
     *
     * El {@code CAST} es necesario: sin el, PostgreSQL no sabe de que tipo es el parametro
     * cuando llega nulo y falla con "could not determine data type".
     *
     * No se toca una caseta anulada, igual que en el resto de escrituras del plano.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE puesto SET precio = CAST(:precio AS numeric), "
           + "\"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = :usuarioId "
           + "WHERE id = :id AND (\"_estado\" IS NULL OR \"_estado\" <> 'X')", nativeQuery = true)
    int actualizarPrecio(@Param("id") Long id,
                         @Param("precio") java.math.BigDecimal precio,
                         @Param("usuarioId") Long usuarioId);

    /**
     * De un grupo de casetas, cuales NO se pueden renumerar porque arrastran una venta.
     *
     * El numero de la caseta no se copia a la venta: se lee en vivo con un JOIN a puesto
     * (ver fn_get_inscripciones y el recibo en ReciboPdfService), asi que cambiarselo a una
     * caseta vendida reescribiria lo que dice un comprobante ya entregado al expositor.
     * Cuenta tanto la venta historica como la ocupacion actual.
     */
    @Query(value = "SELECT p.id FROM puesto p WHERE p.id IN (:ids) "
           + "AND (p.estado_puesto = 'O' "
           + "     OR EXISTS (SELECT 1 FROM inscripcion_puesto ip WHERE ip.id_puesto = p.id))",
           nativeQuery = true)
    List<Long> idsConVentas(@Param("ids") Collection<Long> ids);

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

    /**
     * Quien tiene cada caseta que NO esta libre: el que la reservo y el que la vendio.
     *
     * Es una sola consulta para las ~530 casetas, igual que
     * {@code findAsignacionesConVendedor}: el mapa la pide una vez, no una por caseta.
     *
     * Son dos mitades porque el dato vive en dos sitios distintos, y esa es justamente la
     * razon de que esta consulta exista:
     *
     * <ul>
     *   <li>La mitad {@code 'T'} lee {@code reservado_por_id_usuario}, que la caseta conserva
     *       mientras dura la reserva.</li>
     *   <li>La mitad {@code 'O'} tiene que ir a la inscripcion, porque al vender ese campo se
     *       pone a NULL. Se toma {@code _registro_id_usuario} de la inscripcion —quien
     *       registro la venta— y no {@code _modificacion_id_usuario} de la caseta: este ultimo
     *       lo pisa cualquier cambio posterior, asi que mover la caseta en el Editor cambiaria
     *       "quien la vendio", que es exactamente la clase de dato que no puede mentir.</li>
     * </ul>
     *
     * Se filtra por la edicion activa y se descartan las inscripciones anuladas
     * ({@code _estado = 'X'}): una caseta vendida y luego cancelada ya no la vendio nadie. El
     * {@code DISTINCT ON} deja la venta viva mas reciente si una caseta tuviera varias.
     *
     * <b>{@code desde} va NULO en la mitad {@code 'T'}, y es a proposito.</b> No existe ninguna
     * columna que diga cuando se tomo la reserva: {@code reservarSiLibre} solo escribe
     * {@code reserva_expira}, y {@code reservarSiLibreOMia} lo <i>reescribe</i> cada vez que el
     * vendedor vuelve a tocar una caseta que ya es suya. Restar el TTL al vencimiento daria un
     * "reservada hace 2 min" que se rejuvenece solo cada vez que toca otra caseta del carrito,
     * que es peor que no decir nada. Para el tiempo, el cliente ya tiene {@code reservaExpira}
     * en la difusion: lo honesto ahi es "vence en X", no "la tomo hace X".
     *
     * OJO con el nombre de la columna: la entidad la declara {@code _registro_idUsuario}, pero
     * la estrategia de nombres de Hibernate la convierte y en PostgreSQL se llama
     * {@code _registro_id_usuario}. En una consulta nativa no hay traduccion, y escribir el
     * nombre de la entidad falla en ejecucion, no al compilar.
     *
     * Columnas: id_puesto, id_usuario, nombre, paterno, materno, celular, username, estado, desde.
     */
    @Query(value = """
            SELECT p.id, u.id, pe.nombre, pe.paterno, pe.materno, pe.celular, u.username,
                   'T', NULL::timestamp
              FROM puesto p
              INNER JOIN usuario u ON u.id = p.reservado_por_id_usuario
              LEFT JOIN persona pe ON pe.id = u.persona_id
             WHERE p.estado_puesto = 'T'
            UNION ALL
            SELECT v.id_puesto, u.id, pe.nombre, pe.paterno, pe.materno, pe.celular, u.username,
                   'O', v.fecha_compra
              FROM (
                    SELECT DISTINCT ON (ip.id_puesto)
                           ip.id_puesto, i."_registro_id_usuario" AS id_usuario, i.fecha_compra
                      FROM inscripcion_puesto ip
                      INNER JOIN inscripcion i ON i.id = ip.id_inscripcion
                      INNER JOIN puesto pu ON pu.id = ip.id_puesto AND pu.estado_puesto = 'O'
                     WHERE ip.id_puesto IS NOT NULL
                       AND (i."_estado" IS NULL OR i."_estado" <> 'X')
                       AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
                       AND i.id_edicion = (SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1)
                     ORDER BY ip.id_puesto, i.fecha_compra DESC NULLS LAST
                   ) v
              INNER JOIN usuario u ON u.id = v.id_usuario
              LEFT JOIN persona pe ON pe.id = u.persona_id
            """, nativeQuery = true)
    List<Object[]> findOcupacionConVendedor();

    /**
     * Cuantas casetas lleva vendidas cada vendedor en la edicion activa.
     *
     * Cuenta CASETAS y no inscripciones: una venta de tres casetas son tres, que es lo que se
     * mira contra el plano. Misma fuente que la mitad {@code 'O'} de
     * {@link #findOcupacionConVendedor}, para que los dos numeros no se puedan contradecir.
     *
     * Columnas: id_usuario, casetas.
     */
    @Query(value = """
            SELECT i."_registro_id_usuario", COUNT(*)
              FROM inscripcion_puesto ip
              INNER JOIN inscripcion i ON i.id = ip.id_inscripcion
              INNER JOIN puesto pu ON pu.id = ip.id_puesto AND pu.estado_puesto = 'O'
             WHERE ip.id_puesto IS NOT NULL
               AND i."_registro_id_usuario" IS NOT NULL
               AND (i."_estado" IS NULL OR i."_estado" <> 'X')
               AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
               AND i.id_edicion = (SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1)
             GROUP BY i."_registro_id_usuario"
            """, nativeQuery = true)
    List<Object[]> contarVendidasPorVendedor();
}
