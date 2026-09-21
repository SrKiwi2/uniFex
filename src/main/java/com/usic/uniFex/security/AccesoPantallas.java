package com.usic.uniFex.security;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.usic.uniFex.model.service.PermisoPantallaService;

import lombok.RequiredArgsConstructor;

/**
 * Acceso por PANTALLA, para las pantallas cuyo permiso se da por usuario.
 *
 * <h2>Por que existe</h2>
 * Los permisos de pantalla se asignan por rol y por usuario (V49), y un rol puede ser uno creado
 * a mano ("ROL LIBRE"). Pero el servidor autorizaba solo por los roles del sistema escritos en
 * {@link Roles}: a un usuario se le daba "Inscripciones", la pantalla se abria, y el servidor le
 * respondia 403 al pedir los datos. Con esto, en las pantallas que lo usan, tener la pantalla
 * basta:
 *
 * <pre>@PreAuthorize(Roles.VE_INSCRIPCIONES + " or @acceso.puede('inscripciones')")</pre>
 *
 * El rol del sistema sigue valiendo por si solo (con "or"), asi que nadie pierde un acceso que
 * ya tenia aunque en su matriz falte la casilla.
 *
 * <h2>La condicion sobre los datos</h2>
 * Entrar a la pantalla no es ver todo: {@link #soloDe} dice a quien hay que recortar. Quien
 * tiene uno de los roles que ven todo (administracion, y los que ya lo veian: verificador o
 * asesoria segun la pantalla) ve todas las ventas; cualquier otro, solo las que registro EL.
 *
 * <h2>Donde NO se usa</h2>
 * En lo que modifica el plano o los usuarios. Ahi una casilla editable desde la web no puede ser
 * lo unico que proteja: sigue siendo solo por rol.
 */
@Component("acceso")
@RequiredArgsConstructor
public class AccesoPantallas {

    private final PermisoPantallaService permisos;

    /** Para {@code @PreAuthorize}: ¿el usuario actual tiene esta pantalla, por su rol o por el? */
    public boolean puede(String pantalla) {
        JwtUser u = actual();
        if (u == null || pantalla == null) return false;
        if (PermisoPantallaService.loVeTodo(u.rol())) return true;
        return permisos.pantallasDe(u.id(), u.rol()).contains(pantalla);
    }

    /**
     * El usuario al que hay que recortar los datos, o {@code null} si ve todo.
     *
     * @param autoridadesQueVenTodo las {@code ROLE_...} que ven todas las ventas en esa pantalla
     * @return null = sin recorte; si no, el id del usuario (solo sus ventas). Sin usuario
     *         autenticado devuelve -1: un id que no existe, para que la lista salga vacia y no
     *         completa si algo se colara.
     */
    public static Long soloDe(List<String> autoridadesQueVenTodo) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return -1L;
        boolean veTodo = a.getAuthorities().stream()
                .anyMatch(g -> autoridadesQueVenTodo.contains(g.getAuthority()));
        if (veTodo) return null;
        return a.getPrincipal() instanceof JwtUser u ? u.id() : -1L;
    }

    private static JwtUser actual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof JwtUser u ? u : null;
    }
}
