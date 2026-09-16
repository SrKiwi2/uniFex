package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IAreaDao;
import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dao.ISeguimientoFacultadDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Area;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seguimiento de la venta de UNA facultad.
 *
 * <h2>Que es una "facultad" aqui</h2>
 * El AREA academica de V35 (ACEF, ACBN, ACYT). Los vendedores cuelgan de ella por su carrera:
 * {@code persona.id_carrera -> carrera.id_area}. No se invento un concepto nuevo de facultad
 * porque seria una segunda verdad sobre lo mismo.
 *
 * <h2>Esto NO informa de dinero</h2>
 * Es un modulo para MIRAR como va la venta, no para cuadrarla: nombre del vendedor, categoria,
 * numeros de caseta y cuantas. Ni precios ni totales en bolivianos. La garantia no esta en la
 * pantalla —una pantalla se cambia sin querer— sino en que las consultas no traen `costo`: no
 * hay importe que se pueda escapar al JSON por descuido.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeguimientoFacultadService {

    private final ISeguimientoFacultadDao dao;
    private final IUsuarioDao usuarioDao;
    private final IAreaDao areaDao;
    private final IEdicionDao edicionDao;

    /** Lo vendido por un vendedor dentro de UNA categoria. */
    public record VentaPorCategoria(Long categoriaId, String categoria,
                                    /** Los numeros de caseta, que es como se nombra lo vendido. */
                                    List<String> casetas,
                                    int cantidad) {
    }

    /** Un vendedor de la facultad y lo que lleva vendido. Sin un solo importe. */
    public record VendedorSeguimiento(Long usuarioId, String usuario, String nombre, String carrera,
                                      List<VentaPorCategoria> porCategoria,
                                      int casetasVendidas) {
    }

    /** El informe completo de una facultad. */
    public record Informe(Long areaId, String sigla, String nombre, String edicion,
                          List<VendedorSeguimiento> vendedores,
                          int totalVendedores, int totalCasetas) {
    }

    /**
     * El area que sigue este usuario, o null si no tiene ninguna.
     *
     * Se resuelve EN EL SERVIDOR a partir del token y nunca se acepta como parametro de quien
     * solo monitorea: si el area viajara en la peticion, cualquiera podria pedir la de otra
     * facultad cambiando un numero en la URL.
     */
    @Transactional(readOnly = true)
    public Area areaDe(Long usuarioId) {
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        return u == null ? null : u.getAreaSeguimiento();
    }

    /**
     * La primera facultad del catalogo, para quien puede mirarlas todas.
     *
     * Administracion no tiene por que tener un area asignada —asigna, no monitorea— y sin esto
     * abria el modulo y le decia "no tienes facultad asignada", que para quien puede ver todas
     * es una respuesta absurda. Con esto entra viendo una y cambia con el selector.
     */
    @Transactional(readOnly = true)
    public Area primeraArea() {
        return areaDao.findAll().stream()
                .filter(a -> !"X".equals(a.getEstado()))
                .findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public Informe informe(Long areaId) {
        Area area = areaDao.findById(areaId).orElse(null);
        if (area == null) return null;

        Long edicionId = edicionDao.findFirstByActivaTrueOrderByAnioDesc()
                .map(e -> e.getId()).orElse(null);
        String edicion = edicionDao.findFirstByActivaTrueOrderByAnioDesc()
                .map(e -> e.getNombre()).orElse(null);

        // Se arranca por TODOS los vendedores de la facultad, hayan vendido o no: quien no ha
        // vendido nada es justo lo que un seguimiento tiene que poder ver.
        Map<Long, VendedorEnCurso> porVendedor = new LinkedHashMap<>();
        for (Object[] f : dao.vendedoresDeArea(areaId)) {
            Long id = numero(f[0]);
            porVendedor.put(id, new VendedorEnCurso(id, texto(f[1]), texto(f[2]), texto(f[3])));
        }

        int totalCasetas = 0;
        for (Object[] f : dao.ventasDeArea(areaId, edicionId)) {
            Long usuarioId = numero(f[0]);
            VendedorEnCurso v = porVendedor.computeIfAbsent(usuarioId,
                    k -> new VendedorEnCurso(usuarioId, texto(f[1]), texto(f[2]), texto(f[3])));
            v.sumar(numero(f[4]), texto(f[5]), texto(f[6]));
            totalCasetas++;
        }

        List<VendedorSeguimiento> vendedores = porVendedor.values().stream()
                .map(VendedorEnCurso::cerrar)
                // Primero quien mas vendio: es el orden en que se lee un seguimiento.
                .sorted((a, b) -> Integer.compare(b.casetasVendidas(), a.casetasVendidas()))
                .toList();

        return new Informe(area.getId(), area.getSigla(), area.getNombre(), edicion,
                vendedores, vendedores.size(), totalCasetas);
    }

    /** Asigna (o quita, con null) la facultad que monitorea un usuario. */
    @Transactional
    public boolean asignar(Long usuarioId, Long areaId, Long adminId) {
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) return false;
        Area area = null;
        if (areaId != null) {
            area = areaDao.findById(areaId).orElse(null);
            if (area == null) throw new IllegalArgumentException("Esa facultad no existe");
        }
        u.setAreaSeguimiento(area);
        u.setModificacion(new Date());
        u.setModificacionIdUsuario(adminId);
        usuarioDao.save(u);
        log.info("El usuario {} pasa a monitorear el area {} (admin {})",
                usuarioId, areaId, adminId);
        return true;
    }

    /** Quien monitorea cada facultad, para la pantalla de configuracion. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> asignaciones() {
        List<Map<String, Object>> fuera = new ArrayList<>();
        for (Usuario u : usuarioDao.findAll()) {
            if (u.getAreaSeguimiento() == null) continue;
            if ("ELIMINADO".equals(u.getEstado())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("usuarioId", u.getId());
            m.put("usuario", u.getUsername());
            m.put("nombre", u.getPersona() == null ? u.getUsername()
                    : String.join(" ", java.util.stream.Stream
                            .of(u.getPersona().getNombre(), u.getPersona().getPaterno())
                            .filter(x -> x != null && !x.isBlank()).toList()));
            m.put("areaId", u.getAreaSeguimiento().getId());
            m.put("sigla", u.getAreaSeguimiento().getSigla());
            fuera.add(m);
        }
        return fuera;
    }

    /** Acumulador mutable mientras se agrupan las filas planas de la consulta. */
    private static final class VendedorEnCurso {
        private final Long id;
        private final String usuario;
        private final String nombre;
        private final String carrera;
        private final Map<Long, Cat> cats = new LinkedHashMap<>();

        VendedorEnCurso(Long id, String usuario, String nombre, String carrera) {
            this.id = id; this.usuario = usuario; this.nombre = nombre; this.carrera = carrera;
        }

        void sumar(Long categoriaId, String categoria, String codigo) {
            cats.computeIfAbsent(categoriaId, k -> new Cat(categoria)).casetas.add(codigo);
        }

        VendedorSeguimiento cerrar() {
            List<VentaPorCategoria> lista = new ArrayList<>();
            int total = 0;
            for (Map.Entry<Long, Cat> e : cats.entrySet()) {
                List<String> casetas = List.copyOf(e.getValue().casetas);
                lista.add(new VentaPorCategoria(e.getKey(), e.getValue().nombre, casetas, casetas.size()));
                total += casetas.size();
            }
            return new VendedorSeguimiento(id, usuario,
                    nombre == null || nombre.isBlank() ? usuario : nombre,
                    carrera, lista, total);
        }

        private static final class Cat {
            final String nombre;
            final List<String> casetas = new ArrayList<>();
            Cat(String nombre) { this.nombre = nombre; }
        }
    }

    private static Long numero(Object o) { return o == null ? null : ((Number) o).longValue(); }
    private static String texto(Object o) { return o == null ? null : o.toString(); }
}
