package com.usic.uniFex.model.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FuncionesApi {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public Integer verificar_inscripcion_puesto(String p_ci, Integer p_id_entidad) {
        String sql = "SELECT * from public.verificar_inscripcion_puesto(?,?);";
        Object[] params = new Object[] {p_ci,p_id_entidad};
    
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, params);
        } catch (EmptyResultDataAccessException e) {
            
            return null;
        }
    }

}
