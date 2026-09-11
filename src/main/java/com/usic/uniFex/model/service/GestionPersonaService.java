package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.dao.IUsuarioDao;
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
    private final IUsuarioDao usuarioDao;

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
        return Set.copyOf(usuarioDao.idsDePersonasConUsuario());
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


    /**
     * Personas para el selector del alta de usuarios.
     *
     * Devuelve las personas del sistema (ACTIVO) que casen con el texto, y ademas, si el texto es
     * un C.I. exacto, la persona que lo tenga aunque NO sea del sistema. Ese anadido resuelve un
     * callejon sin salida real: los responsables de entidad y los promotores traidos de la UAP
     * viven en la misma tabla con {@code _estado} RESPONSABLE / PROMOTOR, asi que no salian en el
     * buscador — pero el alta rechazaba su C.I. por duplicado. El administrador no podia ni
     * encontrarlos ni crearlos. Ahora los encuentra y les puede dar un usuario, que es lo que
     * ocurre cuando un responsable ademas trabaja en la feria.
     *
     * El limite acota la lista: son cientos de personas y un desplegable no las necesita todas.
     */
    public List<Seleccionable> buscarParaSelector(String texto, int limite) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        Set<Long> conUsuario = idsConUsuario();

        List<Persona> encontradas = new ArrayList<>(listar().stream()
                .filter(p -> q.isEmpty() || textoBuscable(p).contains(q))
                .limit(limite)
                .toList());

        if (!q.isEmpty() && encontradas.stream().noneMatch(p -> q.equalsIgnoreCase(trim(p.getCi())))) {
            buscarPorCi(q).filter(p -> encontradas.stream().noneMatch(e -> e.getId().equals(p.getId())))
                    .ifPresent(encontradas::add);
        }

        return encontradas.stream()
                .map(p -> new Seleccionable(p.getId(), nombreCompleto(p), p.getCi(),
                        conUsuario.contains(p.getId()), p.getEstado()))
                .toList();
    }

    /**
     * Persona por C.I. exacto, sea del sistema o no, siempre que no este dada de baja.
     * Es la comprobacion que evita crear dos veces a la misma persona desde el alta de usuarios.
     */
    public Optional<Persona> buscarPorCi(String ci) {
        String buscado = trim(ci);
        if (buscado == null || buscado.isEmpty()) return Optional.empty();
        return personaService.findFirstByCi(buscado)
                .filter(p -> !ELIMINADO.equalsIgnoreCase(p.getEstado()));
    }

    /** Convierte una persona en la forma que consume un selector. */
    public Seleccionable comoSeleccionable(Persona p) {
        return new Seleccionable(p.getId(), nombreCompleto(p), p.getCi(),
                idsConUsuario().contains(p.getId()), p.getEstado());
    }

    /** Una persona tal como la ve un selector: quien es, si ya tiene login y de donde salio. */
    public record Seleccionable(Long id, String nombre, String ci, boolean tieneUsuario, String origen) {
    }

    private String textoBuscable(Persona p) {
        return (nombreCompleto(p) + " " + (p.getCi() == null ? "" : p.getCi())).toLowerCase();
    }

    private static String nombreCompleto(Persona p) {
        return java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b).orElse("(sin nombre)");
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
