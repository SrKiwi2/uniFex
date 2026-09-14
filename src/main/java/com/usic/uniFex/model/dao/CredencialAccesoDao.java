package com.usic.uniFex.model.dao;

import java.util.List;

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
}
