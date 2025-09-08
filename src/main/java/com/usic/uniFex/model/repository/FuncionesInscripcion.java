package com.usic.uniFex.model.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FuncionesInscripcion {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<Map<String, Object>> obtener_puestos_por_inscripcion(Long p_id_inscripcion) {
        String sql = "SELECT * FROM public.obtener_puestos_por_inscripcion(?)";

        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {

            return jdbcTemplate.queryForList(sql, new Object[] {
                p_id_inscripcion
            });

        } catch (SQLException e) {
            throw new RuntimeException("Error al crear array SQL", e);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public BigDecimal obtenerCostoPuesto(Long idTipoEntidad, String tamanoPuesto, Long idCategoria) {
        final String sql = "SELECT public.obtenercostopuesto(?,?,?)";
        try {
            return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> rs.getBigDecimal(1),
                idTipoEntidad, tamanoPuesto, idCategoria
            );
        } catch (EmptyResultDataAccessException e) {
            return BigDecimal.ZERO;
        }
    }
}
