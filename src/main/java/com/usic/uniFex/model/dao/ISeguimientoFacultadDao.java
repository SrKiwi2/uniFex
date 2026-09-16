package com.usic.uniFex.model.dao;

import java.util.List;

/**
 * Lo vendido por los vendedores de una facultad (area academica de V35).
 *
 * <h2>Ninguna consulta de aqui selecciona un importe</h2>
 * El modulo monitorea, no recauda. La garantia de que no se filtre un precio no es esconderlo en
 * la pantalla —eso se cambia sin querer— sino no traerlo: `inscripcion_puesto.costo` no aparece
 * en ningun SELECT de esta clase.
 */
public interface ISeguimientoFacultadDao {

    /**
     * Una fila por CASETA VENDIDA de la facultad.
     * Columnas: usuarioId, username, nombre, carrera, categoriaId, categoria, codigo, entidad.
     */
    List<Object[]> ventasDeArea(Long areaId, Long edicionId);

    /**
     * Los usuarios de la facultad, HAYAN VENDIDO O NO.
     * Columnas: id, username, nombre, carrera.
     */
    List<Object[]> vendedoresDeArea(Long areaId);
}
