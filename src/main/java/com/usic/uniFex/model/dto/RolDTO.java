package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Rol;
import com.usic.uniFex.security.RolesSistema;

/**
 * Vista de un rol para el modulo de gestion.
 *
 * {@code sistema} y {@code usuarios} existen para que la interfaz pueda desactivar los botones
 * antes de que el usuario los pulse: un rol del sistema no se renombra ni se borra, y uno con
 * usuarios asignados tampoco se borra. El backend lo vuelve a comprobar igualmente.
 */
public record RolDTO(
        Long id,
        String nombre,
        String descripcion,
        String autoridad,
        boolean sistema,
        long usuarios,
        String estado) {

    public static RolDTO de(Rol r, long usuarios) {
        return new RolDTO(
                r.getId(),
                r.getNombre(),
                r.getDescripcion(),
                autoridadDe(r.getNombre()),
                RolesSistema.esDelSistema(r.getNombre()),
                usuarios,
                r.getEstado());
    }

    /**
     * La autoridad con la que Spring Security ve este rol. Se muestra en la interfaz porque es
     * el dato que hay que escribir en un {@code @PreAuthorize} y no coincide con el nombre:
     * "SUPER USUARIO" llega como ROLE_SUPER_USUARIO. Misma conversion que JwtUser.rolNormalizado().
     */
    private static String autoridadDe(String nombre) {
        return nombre == null ? "" : "ROLE_" + nombre.toUpperCase().replace(' ', '_');
    }
}
