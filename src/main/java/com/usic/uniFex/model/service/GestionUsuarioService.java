package com.usic.uniFex.model.service;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.dao.IRolDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Rol;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.security.RolesSistema;

import lombok.RequiredArgsConstructor;

/**
 * Altas, bajas y cambios de los usuarios del sistema (la version API/JWT de lo que hace el
 * UsuarioController de Thymeleaf, con las validaciones centralizadas en un solo sitio).
 *
 * Estados de {@code _estado}: ACTIVO (puede entrar), INACTIVO (login rechazado por AuthController)
 * y ELIMINADO (baja logica; no se lista ni cuenta). No hay borrado fisico: otras filas
 * (inscripciones, auditoria) referencian al usuario.
 *
 * Dos reglas de privilegio que no son burocracia:
 *
 * 1. **Solo un SUPER USUARIO toca a otro SUPER USUARIO** (y solo el concede ese rol). Sin esto,
 *    un ADMINISTRADOR —que tambien entra a este modulo— podria cambiarle la contrasena al super
 *    usuario y entrar como el, o simplemente ascenderse a si mismo. Seria un escalado de
 *    privilegios por la puerta principal.
 * 2. **Siempre queda al menos un SUPER USUARIO activo.** De ese agujero no se sale desde la
 *    aplicacion: el modulo que arregla usuarios y roles exige justamente ese rol.
 */
@Service
@RequiredArgsConstructor
public class GestionUsuarioService {

    public static final String ACTIVO = "ACTIVO";
    public static final String INACTIVO = "INACTIVO";
    public static final String ELIMINADO = "ELIMINADO";

    /** Longitud minima de contrasena. Corta, pero deja fuera "123" y el nombre del usuario. */
    public static final int PASSWORD_MIN = 8;

    /** Letras, digitos, punto, guion y guion bajo: lo que se puede teclear sin dudar en un login. */
    private static final Pattern USERNAME_VALIDO = Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");

    /** Valor de {@code exceptoId} cuando no hay que excluir a nadie (ver IUsuarioDao). */
    private static final long NINGUNO = -1L;

    private static final Logger logger = LoggerFactory.getLogger(GestionUsuarioService.class);

    private final IUsuarioDao usuarioDao;
    private final IRolDao rolDao;
    private final IPersonaService personaService;
    private final GestionPersonaService gestionPersona;
    private final PasswordEncoder passwordEncoder;
    private final VendedorAsignacionService vendedorAsignacion;

    /**
     * Quien ejecuta la operacion. El rol viaja junto al id porque las reglas de privilegio de
     * arriba dependen de el, y leerlo del JWT en el controlador evita una consulta por peticion.
     */
    public record Actor(Long id, String rol) {
        public boolean esSuperUsuario() {
            return RolesSistema.SUPER_USUARIO.getNombre().equals(RolesSistema.normalizar(rol));
        }
    }

    /** Resultado de una operacion: ok + mensaje, y el usuario cuando aplica. */
    public record Resultado(boolean ok, String mensaje, Usuario usuario) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado exito(String m, Usuario u) { return new Resultado(true, m, u); }
    }

    /** Usuarios no eliminados (los que se muestran en la gestion), con persona y rol ya cargados. */
    public List<Usuario> listar() {
        return usuarioDao.listarGestionables();
    }

    /**
     * Alta de usuario. La persona puede venir ya elegida ({@code personaId}) o crearse en el mismo
     * paso ({@code personaNueva}) — eso ultimo es lo que evita el ir y venir entre dos modulos para
     * dar de alta a un vendedor.
     *
     * Las dos escrituras van en la MISMA transaccion: si el usuario falla despues (nombre repetido,
     * rol inexistente), la persona recien creada se deshace con el. Sin eso, cada intento fallido
     * dejaria una persona huerfana en la tabla y el segundo intento chocaria con su propio C.I.
     */
    @Transactional
    public Resultado crear(String username, String password, Long personaId,
                           GestionPersonaService.Datos personaNueva, Long rolId, Actor actor) {
        String user = username == null ? null : username.trim();
        String problema = validarUsername(user, null);
        if (problema != null) return Resultado.error(problema);
        problema = validarPassword(password, user);
        if (problema != null) return Resultado.error(problema);
        if (rolId == null) return Resultado.error("Debe elegir un rol.");

        Rol rol = rolDao.findById(rolId).orElse(null);
        if (rol == null || GestionRolService.ELIMINADO.equalsIgnoreCase(rol.getEstado())) {
            return Resultado.error("Rol no encontrado.");
        }
        problema = validarConcesionDeRol(rol, actor);
        if (problema != null) return Resultado.error(problema);

        Persona persona;
        if (personaId != null) {
            persona = personaViva(personaId);
            if (persona == null) return Resultado.error("Persona no encontrada.");
        } else if (personaNueva != null) {
            GestionPersonaService.Resultado alta = gestionPersona.crear(personaNueva, actor.id());
            if (!alta.ok()) return Resultado.error(alta.mensaje());
            persona = alta.persona();
        } else {
            return Resultado.error("Debe elegir una persona o crear una nueva.");
        }

        if (usuarioDao.contarPorPersona(persona.getId(), NINGUNO) > 0) {
            return Resultado.error("Esa persona ya tiene un usuario.");
        }

        Usuario u = new Usuario();
        u.setUsername(user);
        u.setPassword(passwordEncoder.encode(password));
        u.setPersona(persona);
        u.setRol(rol);
        u.setEstado(ACTIVO);
        // La auditoria automatica de JPA esta apagada en este proyecto: las fechas y los ids de
        // quien registra se ponen a mano o quedan nulos.
        u.setRegistro(new Date());
        u.setModificacion(new Date());
        u.setRegistroIdUsuario(actor.id());
        u.setModificacionIdUsuario(actor.id());
        Usuario guardado = usuarioDao.save(u);
        logger.info("Usuario creado: {} con rol {} por usuario {}", user, rol.getNombre(), actor.id());
        return Resultado.exito("Usuario creado.", guardado);
    }

    @Transactional
    public Resultado editar(Long id, String username, Long personaId, Long rolId, Actor actor) {
        Usuario u = usuarioDao.findById(id).orElse(null);
        if (u == null || ELIMINADO.equalsIgnoreCase(u.getEstado())) return Resultado.error("Usuario no encontrado.");

        String problema = protegerSuperUsuario(u, actor, "modificar");
        if (problema != null) return Resultado.error(problema);

        String user = username == null ? null : username.trim();
        problema = validarUsername(user, id);
        if (problema != null) return Resultado.error(problema);

        if (personaId != null && !personaId.equals(u.getPersona() == null ? null : u.getPersona().getId())) {
            if (usuarioDao.contarPorPersona(personaId, id) > 0) return Resultado.error("Esa persona ya tiene un usuario.");
            Persona persona = personaViva(personaId);
            if (persona == null) return Resultado.error("Persona no encontrada.");
            u.setPersona(persona);
        }

        if (rolId != null && !rolId.equals(u.getRol() == null ? null : u.getRol().getId())) {
            Rol rol = rolDao.findById(rolId).orElse(null);
            if (rol == null || GestionRolService.ELIMINADO.equalsIgnoreCase(rol.getEstado())) {
                return Resultado.error("Rol no encontrado.");
            }
            problema = validarConcesionDeRol(rol, actor);
            if (problema != null) return Resultado.error(problema);
            // Bajarle el rol al ultimo super usuario activo deja el sistema sin quien lo administre.
            if (esSuperUsuario(u) && ultimoSuperUsuarioActivo(u)) {
                return Resultado.error("Es el unico SUPER USUARIO activo: nombra otro antes de cambiarle el rol.");
            }
            u.setRol(rol);
        }

        u.setUsername(user);
        u.setModificacion(new Date());
        u.setModificacionIdUsuario(actor.id());
        return Resultado.exito("Usuario actualizado.", usuarioDao.save(u));
    }

    @Transactional
    public Resultado cambiarPassword(Long id, String nueva, Actor actor) {
        Usuario u = usuarioDao.findById(id).orElse(null);
        if (u == null || ELIMINADO.equalsIgnoreCase(u.getEstado())) return Resultado.error("Usuario no encontrado.");

        String problema = protegerSuperUsuario(u, actor, "cambiarle la contrasena a");
        if (problema != null) return Resultado.error(problema);
        problema = validarPassword(nueva, u.getUsername());
        if (problema != null) return Resultado.error(problema);

        u.setPassword(passwordEncoder.encode(nueva));
        u.setModificacion(new Date());
        u.setModificacionIdUsuario(actor.id());
        logger.info("Contrasena cambiada al usuario {} por usuario {}", u.getUsername(), actor.id());
        return Resultado.exito("Contrasena actualizada.", usuarioDao.save(u));
    }

    /** Activa o desactiva. No permite que un admin se desactive a si mismo (se quedaria fuera). */
    @Transactional
    public Resultado cambiarEstado(Long id, boolean activo, Actor actor) {
        if (id.equals(actor.id()) && !activo) return Resultado.error("No puedes desactivar tu propio usuario.");
        Usuario u = usuarioDao.findById(id).orElse(null);
        if (u == null || ELIMINADO.equalsIgnoreCase(u.getEstado())) return Resultado.error("Usuario no encontrado.");

        String problema = protegerSuperUsuario(u, actor, "desactivar a");
        if (problema != null) return Resultado.error(problema);
        if (!activo && esSuperUsuario(u) && ultimoSuperUsuarioActivo(u)) {
            return Resultado.error("Es el unico SUPER USUARIO activo: nombra otro antes de desactivarlo.");
        }

        u.setEstado(activo ? ACTIVO : INACTIVO);
        u.setModificacion(new Date());
        u.setModificacionIdUsuario(actor.id());
        return Resultado.exito(activo ? "Usuario activado." : "Usuario desactivado.", usuarioDao.save(u));
    }

    /** Baja logica. Tampoco puedes eliminarte a ti mismo. */
    @Transactional
    public Resultado eliminar(Long id, Actor actor) {
        if (id.equals(actor.id())) return Resultado.error("No puedes eliminar tu propio usuario.");
        Usuario u = usuarioDao.findById(id).orElse(null);
        if (u == null || ELIMINADO.equalsIgnoreCase(u.getEstado())) return Resultado.error("Usuario no encontrado.");

        String problema = protegerSuperUsuario(u, actor, "eliminar a");
        if (problema != null) return Resultado.error(problema);
        if (esSuperUsuario(u) && ultimoSuperUsuarioActivo(u)) {
            return Resultado.error("Es el unico SUPER USUARIO activo: nombra otro antes de eliminarlo.");
        }

        u.setEstado(ELIMINADO);
        u.setModificacion(new Date());
        u.setModificacionIdUsuario(actor.id());
        // Si era vendedor, sus casetas vuelven al catalogo. Sin esto quedan asignadas a alguien
        // que ya no existe: nadie las ve y nadie puede tomarlas (son exclusivas desde V20).
        vendedorAsignacion.liberarAsignacionesDe(id);
        logger.info("Usuario eliminado (baja logica): {} por usuario {}", u.getUsername(), actor.id());
        return Resultado.exito("Usuario eliminado.", usuarioDao.save(u));
    }

    // ===== validaciones =====

    /**
     * La persona, siempre que no este dada de baja. Una persona ELIMINADA no aparece en ningun
     * listado, asi que colgarle un usuario dejaria una cuenta con un titular invisible.
     */
    private Persona personaViva(Long personaId) {
        Persona p = personaService.findById(personaId);
        return (p != null && !GestionPersonaService.ELIMINADO.equalsIgnoreCase(p.getEstado())) ? p : null;
    }

    private String validarUsername(String user, Long exceptoId) {
        if (user == null || user.isBlank()) return "El nombre de usuario es obligatorio.";
        if (!USERNAME_VALIDO.matcher(user).matches()) {
            return "El nombre de usuario admite de 3 a 50 caracteres, sin espacios ni acentos "
                    + "(letras, numeros, punto, guion y guion bajo).";
        }
        if (usuarioDao.contarPorUsername(user, exceptoId == null ? NINGUNO : exceptoId) > 0) {
            return "El nombre de usuario ya esta en uso.";
        }
        return null;
    }

    private String validarPassword(String password, String username) {
        if (password == null || password.isBlank()) return "La contrasena es obligatoria.";
        if (password.length() < PASSWORD_MIN) {
            return "La contrasena debe tener al menos " + PASSWORD_MIN + " caracteres.";
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            return "La contrasena no puede ser igual al nombre de usuario.";
        }
        return null;
    }

    /** Regla 1: conceder SUPER USUARIO es privilegio de un SUPER USUARIO. */
    private String validarConcesionDeRol(Rol rol, Actor actor) {
        boolean esSuper = RolesSistema.SUPER_USUARIO.getNombre().equals(RolesSistema.normalizar(rol.getNombre()));
        if (esSuper && !actor.esSuperUsuario()) {
            return "Solo un SUPER USUARIO puede asignar el rol SUPER USUARIO.";
        }
        return null;
    }

    /** Regla 1, el otro lado: una cuenta SUPER USUARIO solo la toca otro SUPER USUARIO. */
    private String protegerSuperUsuario(Usuario objetivo, Actor actor, String accion) {
        if (esSuperUsuario(objetivo) && !actor.esSuperUsuario() && !objetivo.getId().equals(actor.id())) {
            return "Solo un SUPER USUARIO puede " + accion + " una cuenta SUPER USUARIO.";
        }
        return null;
    }

    private boolean esSuperUsuario(Usuario u) {
        return u.getRol() != null
                && RolesSistema.SUPER_USUARIO.getNombre().equals(RolesSistema.normalizar(u.getRol().getNombre()));
    }

    /** Regla 2: ¿es el ultimo SUPER USUARIO que puede entrar? */
    private boolean ultimoSuperUsuarioActivo(Usuario u) {
        return usuarioDao.contarActivosPorRol(RolesSistema.SUPER_USUARIO.getNombre(), u.getId()) == 0;
    }
}
