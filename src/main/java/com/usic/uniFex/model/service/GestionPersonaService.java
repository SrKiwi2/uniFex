package com.usic.uniFex.model.service;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Persona;

import lombok.RequiredArgsConstructor;

/**
 * Gestion de las personas "del sistema" (las candidatas a ser usuarios), la version API de lo
 * que el PersonaController de Thymeleaf hace a medias: aquel dejaba `modificar-persona` entero
 * comentado (no guardaba nada) y `registrar-persona` sin fijar el estado. Aqui el CRUD funciona.
 *
 * "Del sistema" = estado ACTIVO. Los responsables (estado RESPONSABLE) y los promotores traidos
 * de la API de la UAP (PROMOTOR) NO aparecen aqui; se gestionan en su propio flujo. Al crear una
 * persona aqui queda ACTIVO, con lo que pasa a estar disponible en el selector del modulo Usuarios
 * —eso resuelve el "solo salen 5 personas" de ese modulo.
 */
@Service
@RequiredArgsConstructor
public class GestionPersonaService {

    public static final String ACTIVO = "ACTIVO";
    public static final String ELIMINADO = "ELIMINADO";

    private final IPersonaService personaService;
    private final IUsuarioService usuarioService;

    public record Datos(String nombre, String paterno, String materno, String ci,
                        String correo, String celular) {
    }

    public record Resultado(boolean ok, String mensaje, Persona persona) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado exito(String m, Persona p) { return new Resultado(true, m, p); }
    }

    /** Personas del sistema (estado ACTIVO). */
    public List<Persona> listar() {
        return personaService.listarPersonas();
    }

    /** Ids de personas que ya tienen un usuario no eliminado (para marcarlas en el listado). */
    public Set<Long> idsConUsuario() {
        return usuarioService.findAll().stream()
                .filter(u -> !"ELIMINADO".equalsIgnoreCase(u.getEstado()))
                .filter(u -> u.getPersona() != null)
                .map(u -> u.getPersona().getId())
                .collect(Collectors.toSet());
    }

    @Transactional
    public Resultado crear(Datos d, Long actorId) {
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);
        if (ciEnUso(d.ci(), null)) return Resultado.error("Ya existe una persona con ese C.I.");

        Persona p = new Persona();
        aplicar(p, d);
        p.setEstado(ACTIVO);
        p.setRegistro(new Date());
        p.setModificacion(new Date());
        p.setRegistroIdUsuario(actorId);
        p.setModificacionIdUsuario(actorId);
        return Resultado.exito("Persona creada.", personaService.save(p));
    }

    @Transactional
    public Resultado editar(Long id, Datos d, Long actorId) {
        Persona p = personaService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) return Resultado.error("Persona no encontrada.");
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);
        if (ciEnUso(d.ci(), id)) return Resultado.error("Ya existe otra persona con ese C.I.");

        aplicar(p, d);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);
        return Resultado.exito("Persona actualizada.", personaService.save(p));
    }

    @Transactional
    public Resultado eliminar(Long id, Long actorId) {
        Persona p = personaService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) return Resultado.error("Persona no encontrada.");
        if (idsConUsuario().contains(id)) {
            return Resultado.error("No se puede eliminar: la persona tiene un usuario. Elimina primero el usuario.");
        }
        p.setEstado(ELIMINADO);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);
        return Resultado.exito("Persona eliminada.", personaService.save(p));
    }

    // ===== helpers =====

    private String validar(Datos d) {
        if (vacio(d.nombre())) return "El nombre es obligatorio.";
        if (vacio(d.paterno()) && vacio(d.materno())) return "Debe tener al menos un apellido.";
        if (vacio(d.ci())) return "El C.I. es obligatorio.";
        return null;
    }

    private void aplicar(Persona p, Datos d) {
        p.setNombre(trim(d.nombre()));
        p.setPaterno(trim(d.paterno()));
        p.setMaterno(trim(d.materno()));
        p.setCi(trim(d.ci()));
        p.setCorreo(trim(d.correo()));
        p.setCelular(trim(d.celular()));
    }

    /** El C.I. no debe repetirse en ninguna persona no eliminada (evita duplicar a la misma persona). */
    private boolean ciEnUso(String ci, Long exceptoId) {
        return personaService.findFirstByCi(trim(ci))
                .filter(p -> !ELIMINADO.equalsIgnoreCase(p.getEstado()))
                .filter(p -> exceptoId == null || !p.getId().equals(exceptoId))
                .isPresent();
    }

    private boolean vacio(String s) { return s == null || s.isBlank(); }
    private String trim(String s) { return s == null ? null : s.trim(); }
}
