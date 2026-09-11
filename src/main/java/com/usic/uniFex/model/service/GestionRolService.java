package com.usic.uniFex.model.service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IRolDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.dto.RolDTO;
import com.usic.uniFex.model.entity.Rol;
import com.usic.uniFex.security.RolesSistema;

import lombok.RequiredArgsConstructor;

/**
 * Alta, baja y edicion de los roles del sistema.
 *
 * Un rol es lo que decide que puede hacer cada usuario, asi que este modulo tiene dos reglas que
 * no son estetica sino correccion:
 *
 * 1. **Los cinco roles de {@link RolesSistema} no se renombran ni se eliminan.** Sus nombres
 *    viajan literales por las expresiones de {@code security.Roles}, por
 *    {@code IUsuarioDao.idsDeAdministracion()} y por el {@code if} por rol de AdminController.
 *    Renombrar "SUPER USUARIO" no daria ningun error de compilacion: dejaria a la administracion
 *    sin permisos, y sin manera de recuperarlos, porque este mismo modulo exige ese rol.
 *
 * 2. **Un rol con usuarios no se elimina.** {@code usuario.rol_id} es NOT NULL: la fila quedaria
 *    apuntando a un rol dado de baja y esos usuarios entrarian con una autoridad fantasma.
 *
 * El nombre se guarda siempre en forma canonica (MAYUSCULAS, sin espacios dobles) porque la
 * autoridad de Spring se deriva de el; "Administrador" y "ADMINISTRADOR" tienen que ser el mismo
 * rol y no dos filas que conceden permisos distintos.
 */
@Service
@RequiredArgsConstructor
public class GestionRolService {

    public static final String ACTIVO = "ACTIVO";
    public static final String ELIMINADO = "ELIMINADO";

    /**
     * Solo letras, digitos y espacios simples. Se prohiben acentos y signos a proposito: el
     * nombre se convierte en la autoridad {@code ROLE_<nombre>} y un rol llamado "ASESORÍA (2)"
     * produce una autoridad que nadie va a poder escribir bien en un {@code hasRole(...)}.
     */
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[A-ZÑ0-9]+( [A-ZÑ0-9]+)*$");

    private static final int NOMBRE_MIN = 3;
    private static final int NOMBRE_MAX = 40;

    /** Lo que cabe en rol.descripcion (varchar(255)). Se valida aqui para no devolver un 500. */
    private static final int DESCRIPCION_MAX = 255;

    private static final Logger logger = LoggerFactory.getLogger(GestionRolService.class);

    private final IRolDao rolDao;
    private final IUsuarioDao usuarioDao;

    public record Datos(String nombre, String descripcion) {
    }

    public record Resultado(boolean ok, String mensaje, Rol rol) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado exito(String m, Rol r) { return new Resultado(true, m, r); }
    }

    /** Roles vivos con cuantos usuarios tiene cada uno, en una sola consulta de conteo. */
    public List<RolDTO> listar() {
        Map<Long, Long> porRol = new HashMap<>();
        for (Object[] fila : usuarioDao.contarUsuariosPorRol()) {
            if (fila[0] != null) porRol.put((Long) fila[0], (Long) fila[1]);
        }
        return rolDao.listarVivos().stream()
                .map(r -> RolDTO.de(r, porRol.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @Transactional
    public Resultado crear(Datos d, Long actorId) {
        String nombre = RolesSistema.normalizar(d == null ? null : d.nombre());
        String problema = validarNombre(nombre);
        if (problema != null) return Resultado.error(problema);
        if (rolDao.contarPorNombre(nombre, -1L) > 0) return Resultado.error("Ya existe un rol con ese nombre.");
        problema = validarDescripcion(d.descripcion());
        if (problema != null) return Resultado.error(problema);

        Rol r = new Rol();
        r.setNombre(nombre);
        r.setDescripcion(limpiar(d.descripcion()));
        r.setEstado(ACTIVO);
        // La auditoria JPA esta apagada en este proyecto: estas cuatro se ponen a mano o quedan nulas.
        r.setRegistro(new Date());
        r.setModificacion(new Date());
        r.setRegistroIdUsuario(actorId);
        r.setModificacionIdUsuario(actorId);
        Rol guardado = rolDao.save(r);
        logger.info("Rol creado: {} (id={}) por usuario {}", nombre, guardado.getId(), actorId);
        return Resultado.exito("Rol creado.", guardado);
    }

    /**
     * De un rol del sistema solo se puede cambiar la descripcion; el nombre esta congelado por la
     * regla 1 de arriba. Se acepta que venga el mismo nombre para no obligar a la interfaz a
     * mandar formularios distintos segun el rol.
     */
    @Transactional
    public Resultado editar(Long id, Datos d, Long actorId) {
        Rol r = rolDao.findById(id).orElse(null);
        if (r == null || ELIMINADO.equalsIgnoreCase(r.getEstado())) return Resultado.error("Rol no encontrado.");

        String nombre = RolesSistema.normalizar(d == null ? null : d.nombre());
        boolean cambiaNombre = !nombre.isBlank() && !nombre.equals(RolesSistema.normalizar(r.getNombre()));

        if (cambiaNombre && RolesSistema.esDelSistema(r.getNombre())) {
            return Resultado.error("\"" + r.getNombre() + "\" es un rol del sistema: su nombre no se puede cambiar, "
                    + "solo la descripcion.");
        }
        if (cambiaNombre) {
            String problema = validarNombre(nombre);
            if (problema != null) return Resultado.error(problema);
            if (rolDao.contarPorNombre(nombre, id) > 0) return Resultado.error("Ya existe un rol con ese nombre.");
            if (RolesSistema.esDelSistema(nombre)) {
                return Resultado.error("\"" + nombre + "\" esta reservado para un rol del sistema.");
            }
            r.setNombre(nombre);
        }

        String malaDescripcion = validarDescripcion(d.descripcion());
        if (malaDescripcion != null) return Resultado.error(malaDescripcion);

        r.setDescripcion(limpiar(d.descripcion()));
        r.setModificacion(new Date());
        r.setModificacionIdUsuario(actorId);
        return Resultado.exito("Rol actualizado.", rolDao.save(r));
    }

    /** Baja logica. Nunca fisica: usuario.rol_id es NOT NULL y hay filas historicas apuntando. */
    @Transactional
    public Resultado eliminar(Long id, Long actorId) {
        Rol r = rolDao.findById(id).orElse(null);
        if (r == null || ELIMINADO.equalsIgnoreCase(r.getEstado())) return Resultado.error("Rol no encontrado.");
        if (RolesSistema.esDelSistema(r.getNombre())) {
            return Resultado.error("\"" + r.getNombre() + "\" es un rol del sistema y no se puede eliminar.");
        }
        long usuarios = usuarioDao.contarPorRol(id);
        if (usuarios > 0) {
            return Resultado.error("No se puede eliminar: " + usuarios + " usuario(s) tienen este rol. "
                    + "Cambiales el rol primero.");
        }
        r.setEstado(ELIMINADO);
        r.setModificacion(new Date());
        r.setModificacionIdUsuario(actorId);
        logger.info("Rol eliminado (baja logica): {} (id={}) por usuario {}", r.getNombre(), id, actorId);
        return Resultado.exito("Rol eliminado.", rolDao.save(r));
    }

    /**
     * Siembra los cinco roles del sistema si faltan y les repone la descripcion si estan sin ella.
     * Lo llama el arranque (UniFexApplication). Es idempotente y nunca pisa una descripcion que
     * alguien haya escrito a mano desde el modulo de roles.
     */
    @Transactional
    public void asegurarRolesDelSistema() {
        for (RolesSistema definicion : RolesSistema.values()) {
            Rol r = rolDao.findFirstByNombreIgnoreCase(definicion.getNombre()).orElse(null);
            if (r == null) {
                r = new Rol();
                r.setNombre(definicion.getNombre());
                r.setDescripcion(definicion.getDescripcion());
                r.setEstado(ACTIVO);
                r.setRegistro(new Date());
                r.setModificacion(new Date());
                rolDao.save(r);
                logger.info("Rol del sistema creado: {}", definicion.getNombre());
                continue;
            }
            boolean cambio = false;
            if (!definicion.getNombre().equals(r.getNombre())) { // "Administrador" -> "ADMINISTRADOR"
                r.setNombre(definicion.getNombre());
                cambio = true;
            }
            if (r.getDescripcion() == null || r.getDescripcion().isBlank()) {
                r.setDescripcion(definicion.getDescripcion());
                cambio = true;
            }
            // Un rol del sistema dado de baja dejaria a sus usuarios sin poder iniciar sesion
            // con permisos; se reactiva.
            if (ELIMINADO.equalsIgnoreCase(r.getEstado()) || r.getEstado() == null || r.getEstado().isBlank()) {
                r.setEstado(ACTIVO);
                cambio = true;
            }
            if (cambio) {
                r.setModificacion(new Date());
                rolDao.save(r);
            }
        }
    }

    /** El rol del sistema por su definicion, para el sembrado de usuarios del arranque. */
    public Rol delSistema(RolesSistema definicion) {
        return rolDao.findFirstByNombreIgnoreCase(definicion.getNombre()).orElse(null);
    }

    // ===== helpers =====

    private String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return "El nombre del rol es obligatorio.";
        if (nombre.length() < NOMBRE_MIN) return "El nombre del rol es demasiado corto (minimo " + NOMBRE_MIN + ").";
        if (nombre.length() > NOMBRE_MAX) return "El nombre del rol es demasiado largo (maximo " + NOMBRE_MAX + ").";
        if (!NOMBRE_VALIDO.matcher(nombre).matches()) {
            return "El nombre solo admite letras sin tilde, numeros y espacios simples "
                    + "(se convierte en el permiso ROLE_" + nombre.replace(' ', '_') + ").";
        }
        return null;
    }

    private String validarDescripcion(String descripcion) {
        if (descripcion != null && descripcion.trim().length() > DESCRIPCION_MAX) {
            return "La descripcion no puede pasar de " + DESCRIPCION_MAX + " caracteres.";
        }
        return null;
    }

    private String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
