package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IPermisoPantallaDao;
import com.usic.uniFex.security.PantallasSistema;
import com.usic.uniFex.security.RolesSistema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Que pantallas ve cada rol.
 *
 * <h2>La regla que impide quedarse fuera</h2>
 * {@code SUPER USUARIO} ve TODAS las pantallas siempre, sin pasar por la tabla y sin que se
 * pueda editar. Es deliberado: el modulo que arregla los permisos es, el mismo, una pantalla.
 * Si se pudiera desmarcar, bastaria un descuido para dejar el sistema sin nadie capaz de
 * entrar a corregirlo — y no habria forma de arreglarlo desde la interfaz.
 *
 * <h2>Esto NO autoriza</h2>
 * Decide el menu y la entrada a las rutas de la SPA. Cada endpoint del servidor sigue
 * protegido por {@code @PreAuthorize} con {@link com.usic.uniFex.security.Roles}. Marcar una
 * casilla aqui no abre ninguna puerta en el backend.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermisoPantallaService {

    private final IPermisoPantallaDao dao;

    /** true si ese rol lo ve todo pase lo que pase. */
    public static boolean loVeTodo(String nombreRol) {
        return RolesSistema.SUPER_USUARIO.getNombre()
                .equals(RolesSistema.normalizar(nombreRol));
    }

    private static List<String> todasLasClaves() {
        return PantallasSistema.todas().stream().map(PantallasSistema::getClave).toList();
    }

    /** Las pantallas que puede ver este usuario, ya resueltas. */
    @Transactional(readOnly = true)
    public List<String> pantallasDe(Long usuarioId, String nombreRol) {
        if (loVeTodo(nombreRol)) return todasLasClaves();
        return dao.pantallasDeUsuario(usuarioId);
    }

    /** La matriz completa para la pantalla de administracion. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> matriz() {
        Map<Long, Map<String, Object>> porRol = new LinkedHashMap<>();
        for (Object[] f : dao.listarTodo()) {
            Long rolId = ((Number) f[0]).longValue();
            String nombre = (String) f[1];
            String pantalla = (String) f[2];

            Map<String, Object> fila = porRol.computeIfAbsent(rolId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rolId", rolId);
                m.put("rol", nombre);
                boolean todo = loVeTodo(nombre);
                m.put("loVeTodo", todo);
                m.put("sistema", RolesSistema.esDelSistema(nombre));
                // Se devuelve la lista completa para el super usuario: la pantalla lo enseña
                // todo marcado y en gris, que es mas honesto que enseñarlo vacio.
                m.put("pantallas", todo ? new ArrayList<>(todasLasClaves()) : new ArrayList<String>());
                return m;
            });

            if (pantalla != null && !loVeTodo(nombre)) {
                @SuppressWarnings("unchecked")
                List<String> ps = (List<String>) fila.get("pantallas");
                ps.add(pantalla);
            }
        }
        return new ArrayList<>(porRol.values());
    }

    /**
     * Guarda la seleccion de un rol.
     *
     * @return las claves realmente guardadas (se descartan las que no existen en el catalogo)
     */
    @Transactional
    public List<String> guardar(Long rolId, String nombreRol, List<String> pantallas, Long adminId) {
        if (loVeTodo(nombreRol)) {
            throw new IllegalArgumentException(
                    "El SUPER USUARIO ve todas las pantallas y no se puede limitar: es la forma de "
                    + "recuperar el sistema si alguien se equivoca aqui.");
        }
        List<String> validas = (pantallas == null ? List.<String>of() : pantallas).stream()
                .map(p -> p == null ? "" : p.trim())
                .filter(PantallasSistema::existe)
                .distinct()
                .toList();
        dao.reemplazar(rolId, validas, adminId);
        log.info("Permisos del rol {}: {} pantallas (cambiado por el usuario {})",
                nombreRol, validas.size(), adminId);
        return validas;
    }
}
