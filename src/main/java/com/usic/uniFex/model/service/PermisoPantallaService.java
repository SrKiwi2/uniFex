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
 * Que pantallas ve cada rol y cada usuario.
 *
 * Lo que ve un usuario = las pantallas de su ROL + las que se le dieron a EL (V49). Asi se
 * habilita una pantalla a una persona concreta sin cambiarle el rol ni abrirsela a todo su rol.
 *
 * <h2>La regla que impide quedarse fuera</h2>
 * {@code SUPER USUARIO} ve TODAS las pantallas siempre, sin pasar por la tabla y sin que se
 * pueda editar. Es deliberado: el modulo que arregla los permisos es, el mismo, una pantalla.
 * Si se pudiera desmarcar, bastaria un descuido para dejar el sistema sin nadie capaz de
 * entrar a corregirlo — y no habria forma de arreglarlo desde la interfaz.
 *
 * <h2>Que autoriza y que no</h2>
 * Decide el menu y la entrada a las rutas de la SPA. En la mayoria de pantallas el servidor
 * sigue exigiendo su rol ({@link com.usic.uniFex.security.Roles}). En las de consulta y trabajo
 * con lo propio —Inscripciones, Credenciales, Control de ventas— tener la pantalla tambien da
 * acceso a sus datos, recortados a las ventas del propio usuario si no es administracion (ver
 * {@link com.usic.uniFex.security.AccesoPantallas}). Lo que modifica el plano o los usuarios
 * sigue siendo solo por rol: una casilla no puede ser lo unico que proteja eso.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermisoPantallaService {

    private final com.usic.uniFex.model.dao.IUsuarioDao usuarioDao;
    private final NotificacionService notificaciones;

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

        // Avisar a quien ya esta dentro. Sin esto el cambio no se nota hasta cerrar y volver a
        // abrir la aplicacion, porque el cliente guarda sus permisos en disco para que el menu
        // no parpadee al arrancar. En el APK "cerrar y volver a abrir" puede tardar dias.
        for (Long usuarioId : usuarioDao.idsPorRol(rolId)) {
            notificaciones.notificar(usuarioId, NotificacionService.TIPO_PERMISOS,
                    "Cambiaron tus opciones", "Se actualizo lo que puedes ver en el menu.",
                    null, null);
        }
        return validas;
    }

    // ------------------------------------------------------------------ por usuario (V49)

    /** Los usuarios para elegir en "Permisos por usuario", con cuantas pantallas propias tienen. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> usuarios() {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Object[] f : dao.listarUsuarios()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("usuarioId", ((Number) f[0]).longValue());
            m.put("username", f[1]);
            m.put("nombre", f[2]);
            m.put("rol", f[3]);
            m.put("estado", f[4]);
            m.put("propias", f[5] == null ? 0 : ((Number) f[5]).intValue());
            m.put("loVeTodo", loVeTodo((String) f[3]));
            r.add(m);
        }
        return r;
    }

    /** Lo que ve un usuario, separado en lo que le da su rol y lo que se le dio a el. */
    @Transactional(readOnly = true)
    public Map<String, Object> deUsuario(Long usuarioId) {
        Object[] u = dao.usuario(usuarioId);
        if (u == null) throw new IllegalArgumentException("El usuario no existe");
        String rol = (String) u[3];
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usuarioId", usuarioId);
        m.put("username", u[1]);
        m.put("nombre", u[2]);
        m.put("rol", rol);
        m.put("loVeTodo", loVeTodo(rol));
        m.put("delRol", loVeTodo(rol) ? todasLasClaves() : dao.pantallasDelRolDeUsuario(usuarioId));
        m.put("propias", dao.pantallasPropiasDeUsuario(usuarioId));
        return m;
    }

    /**
     * Guarda las pantallas PROPIAS de un usuario (las que tiene aparte de su rol).
     *
     * No se guardan las que ya le da su rol: repetirlas haria que quitarselas al rol no se las
     * quitara a el, y nadie sabria por que sigue viendolas.
     *
     * @return las claves realmente guardadas
     */
    @Transactional
    public List<String> guardarDeUsuario(Long usuarioId, List<String> pantallas, Long adminId) {
        Object[] u = dao.usuario(usuarioId);
        if (u == null) throw new IllegalArgumentException("El usuario no existe");
        String rol = (String) u[3];
        if (loVeTodo(rol)) {
            throw new IllegalArgumentException("Un SUPER USUARIO ya ve todas las pantallas.");
        }
        List<String> delRol = dao.pantallasDelRolDeUsuario(usuarioId);
        List<String> validas = (pantallas == null ? List.<String>of() : pantallas).stream()
                .map(p -> p == null ? "" : p.trim())
                .filter(PantallasSistema::existe)
                .filter(p -> !delRol.contains(p))
                .distinct()
                .toList();
        dao.reemplazarDeUsuario(usuarioId, validas, adminId);
        log.info("Pantallas propias del usuario {} ({}): {} (cambiado por el usuario {})",
                usuarioId, u[1], validas, adminId);
        // Mismo motivo que al cambiar un rol: sin aviso, el menu no cambia hasta reabrir la app.
        notificaciones.notificar(usuarioId, NotificacionService.TIPO_PERMISOS,
                "Cambiaron tus opciones", "Se actualizo lo que puedes ver en el menu.", null, null);
        return validas;
    }
}
