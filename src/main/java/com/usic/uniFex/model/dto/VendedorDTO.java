package com.usic.uniFex.model.dto;

import java.util.List;

import com.usic.uniFex.model.entity.Area;
import com.usic.uniFex.model.entity.Carrera;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Usuario;

/**
 * Una fila de la pantalla de Vendedores.
 *
 * Es un DTO y no la entidad {@code Usuario} por dos razones. La primera es que devolver la
 * entidad arrastraba el hash de la contraseña por la red en cada listado. La segunda es que
 * esta pantalla necesita cosas que no estan en el usuario: de que carrera y area es, y cuantas
 * casetas lleva de cada categoria — y esa cuenta sale de otra tabla.
 *
 * Carrera y area llegan APLANADAS (no como objetos anidados) porque la tabla las pinta tal
 * cual y el filtro compara contra {@code areaId}; anidarlas obligaria al cliente a navegar dos
 * niveles para leer una sigla.
 */
public record VendedorDTO(
        Long id,
        String username,
        Long personaId,
        String persona,
        String celular,
        Long rolId,
        String rol,
        String estado,
        Long carreraId,
        String carrera,
        Long areaId,
        String areaSigla,
        String areaNombre,
        /** Cuantas casetas lleva en total, ya sumadas: la tabla no tiene que sumarlas otra vez. */
        int totalPuestos,
        /** Desglose por categoria, que es como se piensa el reparto del plano. */
        List<CategoriaAsignada> categorias) {

    /** Cuantas casetas de UNA categoria tiene habilitadas este vendedor. */
    public record CategoriaAsignada(Long categoriaId, String categoria, int cantidad) {
    }

    public static VendedorDTO de(Usuario u, List<CategoriaAsignada> categorias) {
        Persona p = u.getPersona();
        Carrera c = p != null ? p.getCarrera() : null;
        Area a = c != null ? c.getArea() : null;
        return new VendedorDTO(
                u.getId(),
                u.getUsername(),
                p != null ? p.getId() : null,
                nombreCompleto(p),
                p != null ? p.getCelular() : null,
                u.getRol() != null ? u.getRol().getId() : null,
                u.getRol() != null ? u.getRol().getNombre() : null,
                u.getEstado(),
                c != null ? c.getId() : null,
                c != null ? c.getNombre() : null,
                a != null ? a.getId() : null,
                a != null ? a.getSigla() : null,
                a != null ? a.getNombre() : null,
                categorias.stream().mapToInt(CategoriaAsignada::cantidad).sum(),
                categorias);
    }

    private static String nombreCompleto(Persona p) {
        if (p == null) return null;
        return java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("(sin nombre)");
    }
}
