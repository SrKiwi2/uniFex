package com.usic.uniFex.model.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Contador de visitas de las paginas publicas (V38, tabla {@code contador_visitas}).
 *
 * El incremento es UNA sentencia ({@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}):
 * PostgreSQL serializa la fila y dos visitas simultaneas nunca se pisan. Leer el total, sumar
 * en Java y guardar perderia visitas justo en los momentos de mas trafico.
 *
 * El servidor solo suma; que una recarga no cuente dos veces lo decide el cliente (una visita
 * por navegador y dia, ver FeriaPublica.vue). Es un numero orientativo, no una metrica de
 * auditoria: el endpoint es publico y cualquiera puede llamarlo.
 */
@Service
@RequiredArgsConstructor
public class VisitasPaginaService {

    /** Clave de la vista publica de la feria (/feria) en {@code contador_visitas.pagina}. */
    public static final String FERIA_PUBLICA = "feria-publica";

    private final JdbcTemplate jdbc;

    /** Suma una visita a {@code pagina} y devuelve el total resultante. */
    public long registrar(String pagina) {
        Long total = jdbc.queryForObject(
                "INSERT INTO contador_visitas (pagina, total, ultima_visita) VALUES (?, 1, now()) "
                + "ON CONFLICT (pagina) DO UPDATE SET total = contador_visitas.total + 1, "
                + "ultima_visita = now() RETURNING total",
                Long.class, pagina);
        return total != null ? total : 0L;
    }

    /** Total de visitas de {@code pagina}; 0 si todavia no tuvo ninguna. */
    public long total(String pagina) {
        List<Long> fila = jdbc.queryForList(
                "SELECT total FROM contador_visitas WHERE pagina = ?", Long.class, pagina);
        return fila.isEmpty() ? 0L : fila.get(0);
    }
}
