package com.usic.uniFex.model.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IRolService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Rol;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Altas, bajas y cambios de los usuarios del sistema (la version API/JWT de lo que hace el
 * UsuarioController de Thymeleaf, con las validaciones centralizadas en un solo sitio).
 *
 * Estados de {@code _estado}: ACTIVO (puede entrar), INACTIVO (login rechazado por AuthController)
 * y ELIMINADO (baja logica; no se lista ni cuenta). No hay borrado fisico: otras filas
 * (inscripciones, auditoria) referencian al usuario.
 */
@Service
@RequiredArgsConstructor
public class GestionUsuarioService {

    public static final String ACTIVO = "ACTIVO";
    public static final String INACTIVO = "INACTIVO";
    public static final String ELIMINADO = "ELIMINADO";

    private final IUsuarioService usuarioService;
    private final IPersonaService personaService;
    private final IRolService rolService;
    private final PasswordEncoder passwordEncoder;

    /** Resultado de una operacion: ok + mensaje, y el usuario cuando aplica. */
    public record Resultado(boolean ok, String mensaje, Usuario usuario) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado exito(String m, Usuario u) { return new Resultado(true, m, u); }
    }

    /** Usuarios no eliminados (los que se muestran en la gestion). */
    public List<Usuario> listar() {
        return usuarioService.findAll().stream()
                .filter(u -> !ELIMINADO.equalsIgnoreCase(u.getEstado()))
                .toList();
    }

    @Transactional
    public Resultado crear(String username, String password, Long personaId, Long rolId, Long actorId) {
        String user = username == null ? null : username.trim();
        if (vacio(user)) return Resultado.error("El nombre de usuario es obligatorio.");
        if (vacio(password)) return Resultado.error("La contraseña es obligatoria.");
        if (personaId == null) return Resultado.error("Debe elegir una persona.");
        if (rolId == null) return Resultado.error("Debe elegir un rol.");
        if (usernameEnUso(user, null)) return Resultado.error("El nombre de usuario ya está en uso.");
        if (personaYaTieneUsuario(personaId, null)) return Resultado.error("Esa persona ya tiene un usuario.");

        Persona persona = personaService.findById(personaId);
        if (persona == null) return Resultado.error("Persona no encontrada.");
        Rol rol = rolService.findById(rolId);
        if (rol == null) return Resultado.error("Rol no encontrado.");

        Usuario u = new Usuario();
        u.setUsername(user);
        u.setPassword(passwordEncoder.encode(password));
        u.setPersona(persona);
        u.setRol(rol);
        u.setEstado(ACTIVO);
        u.setRegistroIdUsuario(actorId);
        u.setModificacionIdUsuario(actorId);
        return Resultado.exito("Usuario creado.", usuarioService.save(u));
    }

    @Transactional
    public Resultado editar(Long id, String username, Long personaId, Long rolId, Long actorId) {
        Usuario u = usuarioService.findById(id);
        if (u == null || ELIMINADO.equalsIgnoreCase(u.getEstado())) return Resultado.error("Usuario no encontrado.");

        String user = username == null ? null : username.trim();
        if (vacio(user)) return Resultado.error("El nombre de usuario es obligatorio.");
        if (usernameEnUso(user, id)) return Resultado.error("El nombre de usuario ya está en uso.");
        if (personaId != null && personaYaTieneUsuario(personaId, id)) return Resultado.error("Esa persona ya tiene un usuario.");

        u.setUsername(user);
        if (personaId != null) {
            Persona persona = personaService.findById(personaId);
            if (persona == null) return Resultado.error("Persona no encontrada.");
            u.setPersona(persona);
        }
        if (rolId != null) {
            Rol rol = rolService.findById(rolId);
            if (rol == null) return Resultado.error("Rol no encontrado.");
            u.setRol(rol);
        }
        u.setModificacionIdUsuario(actorId);
        return Resultado.exito("Usuario actualizado.", usuarioService.save(u));
    }

    @Transactional
    public Resultado cambiarPassword(Long id, String nueva) {
        Usuario u = usuarioService.findById(id);
        if (u == null || ELIMINADO.equalsIgnoreCase(u.getEstado())) return Resultado.error("Usuario no encontrado.");
        if (vacio(nueva)) return Resultado.error("La contraseña nueva es obligatoria.");
        u.setPassword(passwordEncoder.encode(nueva));
        return Resultado.exito("Contraseña actualizada.", usuarioService.save(u));
    }

    /** Activa o desactiva. No permite que un admin se desactive a si mismo (se quedaria fuera). */
    @Transactional
    public Resultado cambiarEstado(Long id, boolean activo, Long actorId) {
        if (id.equals(actorId) && !activo) return Resultado.error("No puedes desactivar tu propio usuario.");
        Usuario u = usuarioService.findById(id);
        if (u == null || ELIMINADO.equalsIgnoreCase(u.getEstado())) return Resultado.error("Usuario no encontrado.");
        u.setEstado(activo ? ACTIVO : INACTIVO);
        u.setModificacionIdUsuario(actorId);
        return Resultado.exito(activo ? "Usuario activado." : "Usuario desactivado.", usuarioService.save(u));
    }

    /** Baja logica. Tampoco puedes eliminarte a ti mismo. */
    @Transactional
    public Resultado eliminar(Long id, Long actorId) {
        if (id.equals(actorId)) return Resultado.error("No puedes eliminar tu propio usuario.");
        Usuario u = usuarioService.findById(id);
        if (u == null || ELIMINADO.equalsIgnoreCase(u.getEstado())) return Resultado.error("Usuario no encontrado.");
        u.setEstado(ELIMINADO);
        u.setModificacionIdUsuario(actorId);
        return Resultado.exito("Usuario eliminado.", usuarioService.save(u));
    }

    // ===== helpers =====

    private boolean vacio(String s) { return s == null || s.isBlank(); }

    private boolean usernameEnUso(String username, Long exceptoId) {
        return usuarioService.findAll().stream()
                .filter(u -> !ELIMINADO.equalsIgnoreCase(u.getEstado()))
                .filter(u -> exceptoId == null || !u.getId().equals(exceptoId))
                .anyMatch(u -> username.equalsIgnoreCase(u.getUsername()));
    }

    private boolean personaYaTieneUsuario(Long personaId, Long exceptoId) {
        return usuarioService.findAll().stream()
                .filter(u -> !ELIMINADO.equalsIgnoreCase(u.getEstado()))
                .filter(u -> exceptoId == null || !u.getId().equals(exceptoId))
                .anyMatch(u -> u.getPersona() != null && personaId.equals(u.getPersona().getId()));
    }
}
