package com.usic.uniFex.model.IServiceImp;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.FuncionesInscripcionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FuncionesInscripcionServiceImpl implements FuncionesInscripcionService{
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public List<Map<String, Object>> obtenerPuestosPorInscripcion(Long idInscripcion) {
        String sql = """
            SELECT p.codigo, p.tamano, p.estado_puesto, ip.costo
            FROM inscripcion_puesto ip
            JOIN puesto p ON p.id = ip.id_puesto
            WHERE ip.id_inscripcion = ?
        """;
        return jdbcTemplate.queryForList(sql, idInscripcion);
    }
    
}
