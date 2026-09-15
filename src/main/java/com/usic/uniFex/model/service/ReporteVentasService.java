package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dto.ReporteVentaDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class ReporteVentasService {

    @PersistenceContext
    private EntityManager em;

    public record Filtros(LocalDate desde, LocalDate hasta, String responsable,
                          List<Long> categorias, List<Long> promotores) {
    }

    @Transactional(readOnly = true)
    public List<ReporteVentaDTO> buscar(Filtros filtros) {
        Filtros f = filtros == null ? new Filtros(null, null, null, List.of(), List.of()) : filtros;
        StringBuilder sql = new StringBuilder("""
                select i.id,
                       i.fecha_compra,
                       e.nombre as entidad,
                       nullif(btrim(concat_ws(' ', vp.nombre, vp.paterno, vp.materno)), '') as promotor,
                       string_agg(distinct nullif(btrim(concat_ws(' ', rp.nombre, rp.paterno, rp.materno)), ''), ', ' order by nullif(btrim(concat_ws(' ', rp.nombre, rp.paterno, rp.materno)), '')) as responsables,
                       string_agg(distinct c.nombre, ', ' order by c.nombre) as categorias,
                       string_agg(p.codigo, ', ' order by p.codigo) as casetas,
                       count(ip.id)::int as cantidad_casetas,
                       coalesce(sum(ip.costo), 0) as total_bs,
                       (i.img_comprobante is not null and i.img_comprobante <> '') as con_comprobante
                  from inscripcion i
                  join entidad e on e.id = i.id_entidad
                  join inscripcion_puesto ip on ip.id_inscripcion = i.id and ip.id_puesto is not null
                  join puesto p on p.id = ip.id_puesto
                  left join categoria c on c.id = p.id_categoria
                  left join usuario u on u.id = i._registro_id_usuario
                  left join persona vp on vp.id = u.persona_id
                  left join responsable r on r.id_entidad = e.id and (r."_estado" is null or r."_estado" <> 'X')
                  left join persona rp on rp.id = r.id_persona
                 where i."_estado" <> 'X'
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        if (f.desde() != null) {
            sql.append(" and date(i.fecha_compra) >= :desde");
            params.put("desde", f.desde());
        }
        if (f.hasta() != null) {
            sql.append(" and date(i.fecha_compra) <= :hasta");
            params.put("hasta", f.hasta());
        }
        String responsable = limpio(f.responsable());
        if (responsable != null) {
            sql.append(" and exists (select 1 from responsable rx join persona px on px.id = rx.id_persona"
                    + " where rx.id_entidad = e.id and (rx.\"_estado\" is null or rx.\"_estado\" <> 'X')"
                    + " and upper(concat_ws(' ', px.nombre, px.paterno, px.materno, px.ci)) like :responsable)");
            params.put("responsable", "%" + responsable.toUpperCase(java.util.Locale.ROOT) + "%");
        }
        if (f.categorias() != null && !f.categorias().isEmpty()) {
            String in = parametrosLista("categorias", f.categorias(), params);
            sql.append(" and exists (select 1 from inscripcion_puesto ipx join puesto px on px.id = ipx.id_puesto"
                    + " where ipx.id_inscripcion = i.id and px.id_categoria in (" + in + "))");
        }
        if (f.promotores() != null && !f.promotores().isEmpty()) {
            sql.append(" and i._registro_id_usuario in (" + parametrosLista("promotores", f.promotores(), params) + ")");
        }
        sql.append(" group by i.id, i.fecha_compra, e.nombre, vp.nombre, vp.paterno, vp.materno, i.img_comprobante"
                + " order by i.fecha_compra desc nulls last, e.nombre asc");

        var q = em.createNativeQuery(sql.toString());
        params.forEach(q::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> filas = q.getResultList();
        return filas.stream().map(this::de).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> filtros() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("categorias", opciones("""
                select c.id, c.nombre from categoria c
                 where c."_estado" is null or c."_estado" <> 'X'
                 order by c.nombre
                """));
        r.put("promotores", opciones("""
                select distinct u.id, coalesce(nullif(btrim(concat_ws(' ', p.nombre, p.paterno, p.materno)), ''), u.username) as nombre
                  from inscripcion i
                  join usuario u on u.id = i._registro_id_usuario
                  left join persona p on p.id = u.persona_id
                 where i."_estado" <> 'X'
                 order by nombre
                """));
        return r;
    }

    private List<Map<String, Object>> opciones(String sql) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(sql).getResultList();
        List<Map<String, Object>> r = new ArrayList<>();
        for (Object[] f : filas) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) f[0]).longValue());
            m.put("nombre", f[1] == null ? "(sin nombre)" : f[1].toString());
            r.add(m);
        }
        return r;
    }

    private String parametrosLista(String prefijo, List<Long> valores, Map<String, Object> params) {
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < valores.size(); i++) {
            String nombre = prefijo + i;
            nombres.add(":" + nombre);
            params.put(nombre, valores.get(i));
        }
        return String.join(",", nombres);
    }

    private ReporteVentaDTO de(Object[] f) {
        Timestamp ts = (Timestamp) f[1];
        return new ReporteVentaDTO(
                ((Number) f[0]).longValue(),
                ts == null ? null : ts.toLocalDateTime(),
                texto(f[2]), texto(f[3]), texto(f[4]), texto(f[5]), texto(f[6]),
                f[7] == null ? 0 : ((Number) f[7]).intValue(),
                f[8] instanceof BigDecimal b ? b : BigDecimal.ZERO,
                Boolean.TRUE.equals(f[9]));
    }

    private static String texto(Object v) {
        return v == null || v.toString().isBlank() ? "-" : v.toString();
    }

    private static String limpio(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
