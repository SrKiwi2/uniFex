package com.usic.uniFex.model.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Entradas y salidas de la puerta.
 *
 * Con {@code JdbcTemplate} y no con una entidad JPA a proposito: aqui solo se INSERTA y se
 * CUENTA. Una entidad traeria un ciclo de vida, un contexto de persistencia y relaciones que
 * nadie va a recorrer, para una tabla que solo crece.
 */
@Repository
@RequiredArgsConstructor
public class CredencialAccesoDao {

    public static final String ENTRADA = "E";
    public static final String SALIDA = "S";

    private final JdbcTemplate jdbc;

    /** Anota un movimiento. Nunca actualiza: cada escaneo es una fila nueva. */
    public void registrar(Long responsableId, String sentido, Long usuarioId, String origen) {
        jdbc.update("INSERT INTO credencial_acceso (id_responsable, sentido, registrado_por, origen)"
                + " VALUES (?, ?, ?, ?)", responsableId, sentido, usuarioId, origen);
    }

    /**
     * El ultimo sentido registrado, o null si nunca paso por la puerta.
     *
     * Es lo que responde "¿esta dentro?": no hay una columna de estado, se deduce del ultimo
     * movimiento. Asi un escaneo equivocado se arregla registrando el contrario, sin dejar un
     * estado mentiroso que alguien tenga que corregir a mano.
     */
    public String ultimoSentido(Long responsableId) {
        List<String> r = jdbc.queryForList(
                "SELECT sentido FROM credencial_acceso WHERE id_responsable = ?"
                + " ORDER BY cuando DESC, id DESC LIMIT 1", String.class, responsableId);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Cuantas entradas y cuantas salidas lleva esta persona. */
    public int[] conteo(Long responsableId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN sentido = 'E' THEN 1 ELSE 0 END), 0),"
                + "     COALESCE(SUM(CASE WHEN sentido = 'S' THEN 1 ELSE 0 END), 0)"
                + " FROM credencial_acceso WHERE id_responsable = ?",
                (rs, n) -> new int[] { rs.getInt(1), rs.getInt(2) }, responsableId);
    }

    /** Los ultimos movimientos de esta persona, del mas nuevo al mas viejo. */
    public List<Object[]> historial(Long responsableId, int limite) {
        return jdbc.query(
                "SELECT ca.sentido, ca.cuando, u.username, ca.origen"
                + " FROM credencial_acceso ca"
                + " LEFT JOIN usuario u ON u.id = ca.registrado_por"
                + " WHERE ca.id_responsable = ?"
                + " ORDER BY ca.cuando DESC, ca.id DESC LIMIT ?",
                (rs, n) -> new Object[] { rs.getString(1), rs.getTimestamp(2),
                                          rs.getString(3), rs.getString(4) },
                responsableId, limite);
    }

    /**
     * Cuanta gente hay DENTRO ahora mismo.
     *
     * Se cuenta a quien tiene una entrada como ultimo movimiento. No se puede hacer restando
     * entradas menos salidas: quien entro dos veces sin salir contaria dos, y en una evacuacion
     * ese numero es exactamente el que no puede estar mal.
     */
    public int dentroAhora() {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT DISTINCT ON (id_responsable) sentido
                      FROM credencial_acceso
                     ORDER BY id_responsable, cuando DESC, id DESC
                ) ultimo WHERE sentido = 'E'
                """, Integer.class);
        return n == null ? 0 : n;
    }

    /**
     * Filtros para listar movimientos de acceso.
     */
    public record Filtros(
            Long categoriaId,
            LocalDateTime desde,
            LocalDateTime hasta,
            String sentido,
            int limite,
            int offset
    ) {}

    /**
     * Lista movimientos de acceso con filtros.
     *
     * Une credencial_acceso con responsable, persona, entidad, inscripcion, puesto y categoria
     * para poder filtrar por categoria y mostrar datos enriquecidos.
     */
    public List<Map<String, Object>> listarConFiltros(Filtros f) {
        StringBuilder sql = new StringBuilder("""
                SELECT ca.id, ca.sentido, ca.cuando, ca.origen, u.username AS registrado_por,
                       r.id AS responsable_id, p.nombre, p.paterno, p.materno, p.ci,
                       e.nombre AS entidad, c.nombre AS categoria, c.id AS categoria_id,
                       string_agg(DISTINCT pu.codigo, ', ' ORDER BY pu.codigo) AS casetas
                FROM credencial_acceso ca
                INNER JOIN responsable r ON r.id = ca.id_responsable
                INNER JOIN persona p ON p.id = r.id_persona
                INNER JOIN entidad e ON e.id = r.id_entidad
                INNER JOIN inscripcion i ON i.id_entidad = e.id
                    AND (i."_estado" IS NULL OR i."_estado" <> 'X')
                LEFT JOIN inscripcion_puesto ip ON ip.id_inscripcion = i.id
                    AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
                LEFT JOIN puesto pu ON pu.id = ip.id_puesto
                LEFT JOIN categoria c ON c.id = pu.id_categoria
                LEFT JOIN usuario u ON u.id = ca.registrado_por
                WHERE 1=1
                """);

        if (f.categoriaId() != null) {
            sql.append(" AND c.id = ").append(f.categoriaId());
        }
        if (f.desde() != null) {
            sql.append(" AND ca.cuando >= '").append(f.desde()).append("'");
        }
        if (f.hasta() != null) {
            sql.append(" AND ca.cuando <= '").append(f.hasta()).append("'");
        }
        if (f.sentido() != null && !f.sentido().isBlank()) {
            sql.append(" AND ca.sentido = '").append(f.sentido().toUpperCase()).append("'");
        }

        sql.append("""
                GROUP BY ca.id, ca.sentido, ca.cuando, ca.origen, u.username,
                         r.id, p.nombre, p.paterno, p.materno, p.ci,
                         e.nombre, c.nombre, c.id
                ORDER BY ca.cuando DESC, ca.id DESC
                LIMIT ? OFFSET ?
                """);

        return jdbc.queryForList(sql.toString(), f.limite(), f.offset());
    }

    /**
     * Cuenta total de movimientos con los mismos filtros (para paginacion).
     */
    public int contarConFiltros(Filtros f) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT ca.id)
                FROM credencial_acceso ca
                INNER JOIN responsable r ON r.id = ca.id_responsable
                INNER JOIN persona p ON p.id = r.id_persona
                INNER JOIN entidad e ON e.id = r.id_entidad
                INNER JOIN inscripcion i ON i.id_entidad = e.id
                    AND (i."_estado" IS NULL OR i."_estado" <> 'X')
                LEFT JOIN inscripcion_puesto ip ON ip.id_inscripcion = i.id
                    AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
                LEFT JOIN puesto pu ON pu.id = ip.id_puesto
                LEFT JOIN categoria c ON c.id = pu.id_categoria
                WHERE 1=1
                """);

        if (f.categoriaId() != null) {
            sql.append(" AND c.id = ").append(f.categoriaId());
        }
        if (f.desde() != null) {
            sql.append(" AND ca.cuando >= '").append(f.desde()).append("'");
        }
        if (f.hasta() != null) {
            sql.append(" AND ca.cuando <= '").append(f.hasta()).append("'");
        }
        if (f.sentido() != null && !f.sentido().isBlank()) {
            sql.append(" AND ca.sentido = '").append(f.sentido().toUpperCase()).append("'");
        }

        return jdbc.queryForObject(sql.toString(), Integer.class);
    }

    /**
     * Resumen por categoria: cuantas entradas/salidas por categoria.
     */
    public List<Map<String, Object>> resumenPorCategoria(LocalDateTime desde, LocalDateTime hasta) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id AS categoria_id, c.nombre AS categoria,
                       COUNT(*) FILTER (WHERE ca.sentido = 'E') AS entradas,
                       COUNT(*) FILTER (WHERE ca.sentido = 'S') AS salidas,
                       COUNT(DISTINCT r.id) AS personas_unicas
                FROM credencial_acceso ca
                INNER JOIN responsable r ON r.id = ca.id_responsable
                INNER JOIN persona p ON p.id = r.id_persona
                INNER JOIN entidad e ON e.id = r.id_entidad
                INNER JOIN inscripcion i ON i.id_entidad = e.id
                    AND (i."_estado" IS NULL OR i."_estado" <> 'X')
                LEFT JOIN inscripcion_puesto ip ON ip.id_inscripcion = i.id
                    AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
                LEFT JOIN puesto pu ON pu.id = ip.id_puesto
                LEFT JOIN categoria c ON c.id = pu.id_categoria
                WHERE 1=1
                """);

        if (desde != null) {
            sql.append(" AND ca.cuando >= '").append(desde).append("'");
        }
        if (hasta != null) {
            sql.append(" AND ca.cuando <= '").append(hasta).append("'");
        }

        sql.append("""
                GROUP BY c.id, c.nombre
                ORDER BY c.nombre
                """);

        return jdbc.queryForList(sql.toString());
    }

    /**
     * Resumen por usuario (quien escaneo): cuantas entradas/salidas registro cada uno.
     */
    public List<Map<String, Object>> resumenPorUsuario(LocalDateTime desde, LocalDateTime hasta) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.username, u.id AS usuario_id,
                       COUNT(*) FILTER (WHERE ca.sentido = 'E') AS entradas,
                       COUNT(*) FILTER (WHERE ca.sentido = 'S') AS salidas,
                       COUNT(DISTINCT ca.id_responsable) AS personas_unicas
                FROM credencial_acceso ca
                LEFT JOIN usuario u ON u.id = ca.registrado_por
                WHERE 1=1
                """);

        if (desde != null) {
            sql.append(" AND ca.cuando >= '").append(desde).append("'");
        }
        if (hasta != null) {
            sql.append(" AND ca.cuando <= '").append(hasta).append("'");
        }

        sql.append("""
                GROUP BY u.id, u.username
                ORDER BY (COUNT(*) FILTER (WHERE ca.sentido = 'E') + COUNT(*) FILTER (WHERE ca.sentido = 'S')) DESC
                """);

        return jdbc.queryForList(sql.toString());
    }

    /**
     * Personas que estan DENTRO ahora, con su categoria.
     */
    public List<Map<String, Object>> quienesEstanDentro() {
        return jdbc.queryForList("""
                SELECT r.id AS responsable_id, p.nombre, p.paterno, p.materno, p.ci,
                       e.nombre AS entidad, c.nombre AS categoria,
                       string_agg(DISTINCT pu.codigo, ', ' ORDER BY pu.codigo) AS casetas,
                       ca.cuando AS ultima_entrada
                FROM (
                    SELECT DISTINCT ON (ca.id_responsable) ca.id_responsable, ca.cuando
                    FROM credencial_acceso ca
                    ORDER BY ca.id_responsable, ca.cuando DESC, ca.id DESC
                ) ca
                INNER JOIN responsable r ON r.id = ca.id_responsable
                INNER JOIN persona p ON p.id = r.id_persona
                INNER JOIN entidad e ON e.id = r.id_entidad
                INNER JOIN inscripcion i ON i.id_entidad = e.id
                    AND (i."_estado" IS NULL OR i."_estado" <> 'X')
                LEFT JOIN inscripcion_puesto ip ON ip.id_inscripcion = i.id
                    AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
                LEFT JOIN puesto pu ON pu.id = ip.id_puesto
                LEFT JOIN categoria c ON c.id = pu.id_categoria
                WHERE (
                    SELECT sentido FROM credencial_acceso ca2
                    WHERE ca2.id_responsable = ca.id_responsable
                    ORDER BY ca2.cuando DESC, ca2.id DESC LIMIT 1
                ) = 'E'
                ORDER BY p.nombre
                """);
    }
}
