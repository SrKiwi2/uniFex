package com.usic.uniFex.model.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.IDependenciaService;
import com.usic.uniFex.model.IService.IPersonalApoyoService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Dependencia;
import com.usic.uniFex.model.entity.PersonalApoyo;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gestión del personal de apoyo de la feria (fotógrafos, azafatas, logística, etc.).
 * Módulo aislado: no toca la tabla persona ni usuario existentes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GestionPersonalApoyoService {

    public static final String ACTIVO = "A";
    public static final String ELIMINADO = "X";

    /**
     * Con que oficio se registra al responsable de una dependencia creada sola (ver
     * {@link #asegurarFichaAlIngresar}). Es el mismo "coordinador" que ofrece el formulario.
     */
    public static final String ROL_RESPONSABLE_DEPENDENCIA = "coordinador";

    private final IPersonalApoyoService personalApoyoService;
    private final IDependenciaService dependenciaService;
    private final IUsuarioService usuarioService;
    private final FileStorageService almacen;

    public record Datos(Long idDependencia, String nombre, String paterno, String materno,
                        String ci, String correo, String celular, String rol,
                        String descripcionTarea) {

        /** Compatibilidad: los clientes que aun no mandan tarea. */
        public Datos(Long idDependencia, String nombre, String paterno, String materno,
                     String ci, String correo, String celular, String rol) {
            this(idDependencia, nombre, paterno, materno, ci, correo, celular, rol, null);
        }
    }

    public record Resultado(boolean ok, String mensaje, PersonalApoyo personal) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado exito(String m, PersonalApoyo p) { return new Resultado(true, m, p); }
    }

    public record DependenciaDatos(String nombre, String descripcion) {
    }

    public record DependenciaResultado(boolean ok, String mensaje, Dependencia dependencia) {
        static DependenciaResultado error(String m) { return new DependenciaResultado(false, m, null); }
        static DependenciaResultado exito(String m, Dependencia d) { return new DependenciaResultado(true, m, d); }
    }

    /** Lista todas las dependencias activas. */
    public List<Dependencia> listarDependencias() {
        return dependenciaService.listarDependencias();
    }

    /** Lista todo el personal de apoyo activo. */
    public List<PersonalApoyo> listarPersonal() {
        return personalApoyoService.listarPersonalApoyo();
    }

    /** Lista personal de una dependencia específica. */
    public List<PersonalApoyo> listarPorDependencia(Long idDependencia) {
        return personalApoyoService.buscarPorDependencia(idDependencia);
    }

    /**
     * La dependencia a la que pertenece quien opera, o vacio si no tiene ficha.
     *
     * El unico puente entre el login y este modulo es el CI: se busca la ficha de
     * personal_apoyo cuyo CI coincida con el de la persona del usuario. Si no hay ficha
     * (un admin que nunca fue registrado como apoyo), no hay dependencia propia.
     */
    public Optional<Dependencia> miDependencia(Long usuarioId) {
        if (usuarioId == null) return Optional.empty();
        Usuario u = usuarioService.findById(usuarioId);
        if (u == null || u.getPersona() == null || u.getPersona().getCi() == null) return Optional.empty();
        return personalApoyoService.buscarActivosPorCi(u.getPersona().getCi().trim()).stream()
                .map(PersonalApoyo::getDependencia)
                .filter(d -> d != null && !ELIMINADO.equalsIgnoreCase(d.getEstado()))
                .findFirst();
    }

    /**
     * Lista lo que ese usuario puede ver: todo si su alcance es nulo (super usuario), o solo
     * su dependencia. El recorte vive aqui y no en el cliente: los ids los manda el navegador.
     *
     * @param idDependenciaAlcance null = sin recorte; si no, solo esa dependencia.
     */
    public List<PersonalApoyo> listarConAlcance(Long idDependenciaAlcance) {
        if (idDependenciaAlcance == null) return listarPersonal();
        return listarPorDependencia(idDependenciaAlcance);
    }

    /**
     * Deja al usuario listo para el modulo al momento de ingresar: si su persona tiene carrera
     * asignada, esa carrera existe como dependencia (se crea si falta, con su mismo nombre) y
     * el queda registrado en ella como coordinador —su responsable—.
     *
     * Es idempotente y conservador: si ya tiene ficha activa en cualquier dependencia, no se
     * toca nada (pudo asignarsela alguien a mano en otra); si no tiene carrera, tampoco. Se
     * llama desde el login dentro de try/catch: un fallo aqui nunca puede impedir entrar.
     */
    @Transactional
    public void asegurarFichaAlIngresar(Long usuarioId) {
        if (usuarioId == null) return;
        Usuario u = usuarioService.findById(usuarioId);
        if (u == null || u.getPersona() == null) return;
        var persona = u.getPersona();
        if (persona.getCarrera() == null || persona.getCarrera().getNombre() == null
                || persona.getCarrera().getNombre().isBlank()) return;
        String nombreDep = persona.getCarrera().getNombre().trim();

        Dependencia dep = dependenciaService.findByNombre(nombreDep).filter(d ->
                !ELIMINADO.equalsIgnoreCase(d.getEstado())).orElse(null);
        if (dep == null) {
            DependenciaResultado creado = crearDependencia(
                    new DependenciaDatos(nombreDep,
                            "Creada automáticamente desde la carrera al ingresar."),
                    usuarioId);
            if (!creado.ok() || creado.dependencia() == null) {
                // Otro ingreso simultaneo pudo crearla: se reintenta leer una vez.
                dep = dependenciaService.findByNombre(nombreDep).filter(d ->
                        !ELIMINADO.equalsIgnoreCase(d.getEstado())).orElse(null);
                if (dep == null) return;
            } else {
                dep = creado.dependencia();
            }
        }

        if (persona.getCi() == null || persona.getCi().isBlank()) return;
        if (!personalApoyoService.buscarActivosPorCi(persona.getCi().trim()).isEmpty()) return;

        crearPersonal(new Datos(dep.getId(), persona.getNombre(), persona.getPaterno(),
                persona.getMaterno(), persona.getCi().trim(), persona.getCorreo(),
                persona.getCelular(), ROL_RESPONSABLE_DEPENDENCIA), usuarioId, dep.getId());
    }

    @Transactional
    public DependenciaResultado crearDependencia(DependenciaDatos d, Long actorId) {
        String falta = validarDependencia(d);
        if (falta != null) return DependenciaResultado.error(falta);
        if (dependenciaService.findByNombre(d.nombre()).isPresent()) {
            return DependenciaResultado.error("Ya existe una dependencia con ese nombre.");
        }

        Dependencia dep = new Dependencia();
        dep.setNombre(d.nombre().trim());
        dep.setDescripcion(d.descripcion() != null ? d.descripcion().trim() : null);
        dep.setEstado(ACTIVO);
        dep.setRegistro(new Date());
        dep.setModificacion(new Date());
        dep.setRegistroIdUsuario(actorId);
        dep.setModificacionIdUsuario(actorId);

        return DependenciaResultado.exito("Dependencia creada.", dependenciaService.save(dep));
    }

    @Transactional
    public DependenciaResultado editarDependencia(Long id, DependenciaDatos d, Long actorId) {
        Dependencia dep = dependenciaService.findById(id);
        if (dep == null || ELIMINADO.equalsIgnoreCase(dep.getEstado())) {
            return DependenciaResultado.error("Dependencia no encontrada.");
        }
        String falta = validarDependencia(d);
        if (falta != null) return DependenciaResultado.error(falta);
        Optional<Dependencia> existente = dependenciaService.findByNombre(d.nombre().trim());
        if (existente.isPresent() && !existente.get().getId().equals(id)) {
            return DependenciaResultado.error("Ya existe otra dependencia con ese nombre.");
        }

        dep.setNombre(d.nombre().trim());
        dep.setDescripcion(d.descripcion() != null ? d.descripcion().trim() : null);
        dep.setModificacion(new Date());
        dep.setModificacionIdUsuario(actorId);

        return DependenciaResultado.exito("Dependencia actualizada.", dependenciaService.save(dep));
    }

    @Transactional
    public DependenciaResultado eliminarDependencia(Long id, Long actorId) {
        Dependencia dep = dependenciaService.findById(id);
        if (dep == null || ELIMINADO.equalsIgnoreCase(dep.getEstado())) {
            return DependenciaResultado.error("Dependencia no encontrada.");
        }
        // Verificar si tiene personal asignado
        if (!personalApoyoService.buscarPorDependencia(id).isEmpty()) {
            return DependenciaResultado.error("No se puede eliminar: la dependencia tiene personal asignado.");
        }

        dep.setEstado(ELIMINADO);
        dep.setModificacion(new Date());
        dep.setModificacionIdUsuario(actorId);
        return DependenciaResultado.exito("Dependencia eliminada.", dependenciaService.save(dep));
    }

    @Transactional
    public Resultado crearPersonal(Datos d, Long actorId) {
        return crearPersonal(d, actorId, null);
    }

    /**
     * Crea con alcance: si {@code idDependenciaAlcance} no es nulo, el registro queda
     * forzado a esa dependencia aunque el cliente mande otra (el selector del formulario
     * se oculta, pero el body se puede falsificar).
     */
    @Transactional
    public Resultado crearPersonal(Datos d, Long actorId, Long idDependenciaAlcance) {
        Long idDep = (idDependenciaAlcance != null) ? idDependenciaAlcance : d.idDependencia();
        if (idDependenciaAlcance != null && d.idDependencia() != null
                && !idDependenciaAlcance.equals(d.idDependencia())) {
            return Resultado.error("Solo puedes registrar personal de tu dependencia.");
        }
        Datos efectivo = new Datos(idDep, d.nombre(), d.paterno(), d.materno(),
                d.ci(), d.correo(), d.celular(), d.rol(), d.descripcionTarea());
        String falta = validarPersonal(efectivo);
        if (falta != null) return Resultado.error(falta);

        Dependencia dep = dependenciaService.findById(efectivo.idDependencia());
        if (dep == null || ELIMINADO.equalsIgnoreCase(dep.getEstado())) {
            return Resultado.error("Dependencia no encontrada.");
        }
        if (personalApoyoService.findByCi(efectivo.ci()).isPresent()) {
            return Resultado.error("Ya existe personal con ese C.I.");
        }

        PersonalApoyo p = new PersonalApoyo();
        aplicar(p, efectivo, dep);
        p.setEstado(ACTIVO);
        p.setRegistro(new Date());
        p.setModificacion(new Date());
        p.setRegistroIdUsuario(actorId);
        p.setModificacionIdUsuario(actorId);

        return Resultado.exito("Personal creado.", personalApoyoService.save(p));
    }

    @Transactional
    public Resultado editarPersonal(Long id, Datos d, Long actorId) {
        return editarPersonal(id, d, actorId, null);
    }

    /** Edita con alcance: fuera de tu dependencia, la ficha ni se toca (ver crearPersonal). */
    @Transactional
    public Resultado editarPersonal(Long id, Datos d, Long actorId, Long idDependenciaAlcance) {
        PersonalApoyo p = personalApoyoService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) {
            return Resultado.error("Personal no encontrado.");
        }
        if (idDependenciaAlcance != null && (p.getDependencia() == null
                || !idDependenciaAlcance.equals(p.getDependencia().getId()))) {
            return Resultado.error("Ese registro no es de tu dependencia.");
        }
        Long idDep = (idDependenciaAlcance != null) ? idDependenciaAlcance : d.idDependencia();
        Datos efectivo = new Datos(idDep, d.nombre(), d.paterno(), d.materno(),
                d.ci(), d.correo(), d.celular(), d.rol(), d.descripcionTarea());
        String falta = validarPersonal(efectivo);
        if (falta != null) return Resultado.error(falta);

        Optional<PersonalApoyo> existente = personalApoyoService.findByCi(efectivo.ci());
        if (existente.isPresent() && !existente.get().getId().equals(id)) {
            return Resultado.error("Ya existe otro personal con ese C.I.");
        }

        Dependencia dep = dependenciaService.findById(efectivo.idDependencia());
        if (dep == null || ELIMINADO.equalsIgnoreCase(dep.getEstado())) {
            return Resultado.error("Dependencia no encontrada.");
        }

        aplicar(p, efectivo, dep);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);

        return Resultado.exito("Personal actualizado.", personalApoyoService.save(p));
    }

    @Transactional
    public Resultado eliminarPersonal(Long id, Long actorId) {
        return eliminarPersonal(id, actorId, null);
    }

    /** Elimina con alcance: fuera de tu dependencia, la ficha ni se toca. */
    @Transactional
    public Resultado eliminarPersonal(Long id, Long actorId, Long idDependenciaAlcance) {
        PersonalApoyo p = personalApoyoService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) {
            return Resultado.error("Personal no encontrado.");
        }
        if (idDependenciaAlcance != null && (p.getDependencia() == null
                || !idDependenciaAlcance.equals(p.getDependencia().getId()))) {
            return Resultado.error("Ese registro no es de tu dependencia.");
        }

        p.setEstado(ELIMINADO);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);
        return Resultado.exito("Personal eliminado.", personalApoyoService.save(p));
    }

    // ===== helpers =====

    /**
     * Guarda o reemplaza la foto de la ficha (la que va en el circulo de la credencial).
     *
     * Con alcance igual que editar: fuera de tu dependencia ni se toca.
     */
    @Transactional
    public Resultado guardarFoto(Long id, MultipartFile archivo, Long actorId,
                                 Long idDependenciaAlcance) {
        log.info("[APOYO-FOTO] inicio: ficha={}, actor={}, alcance={}, archivo={}", id, actorId,
                idDependenciaAlcance, archivo == null ? "NULL"
                        : archivo.getOriginalFilename() + " (" + archivo.getSize() + " bytes, "
                                + archivo.getContentType() + ")");
        if (archivo == null || archivo.isEmpty()) {
            log.warn("[APOYO-FOTO] ficha {}: archivo vacio o ausente", id);
            return Resultado.error("No llegó ninguna imagen.");
        }
        PersonalApoyo p = personalApoyoService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) {
            log.warn("[APOYO-FOTO] ficha {} no encontrada o eliminada", id);
            return Resultado.error("Personal no encontrado.");
        }
        if (idDependenciaAlcance != null && (p.getDependencia() == null
                || !idDependenciaAlcance.equals(p.getDependencia().getId()))) {
            log.warn("[APOYO-FOTO] ficha {} fuera del alcance {}", id, idDependenciaAlcance);
            return Resultado.error("Ese registro no es de tu dependencia.");
        }
        String ruta;
        try {
            ruta = almacen.save(archivo, FileStorageService.Bucket.APOYO,
                    p.getNombreCompleto());
            log.info("[APOYO-FOTO] ficha {}: archivo guardado en {}", id, ruta);
        } catch (java.io.IOException e) {
            log.warn("[APOYO-FOTO] ficha {}: fallo al guardar en disco: {}", id,
                    e.getMessage(), e);
            return Resultado.error("No se pudo guardar la imagen: " + e.getMessage());
        }
        // La anterior NO se borra: si la nueva sale mal, la vieja sigue en disco.
        p.setFoto(ruta);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);
        personalApoyoService.save(p);
        log.info("[APOYO-FOTO] ficha {}: ruta registrada en base", id);
        return Resultado.exito("Foto guardada.", p);
    }

    /** Quita la foto. No borra el archivo: solo deja de referenciarlo. */
    @Transactional
    public Resultado quitarFoto(Long id, Long actorId, Long idDependenciaAlcance) {
        PersonalApoyo p = personalApoyoService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) {
            return Resultado.error("Personal no encontrado.");
        }
        if (idDependenciaAlcance != null && (p.getDependencia() == null
                || !idDependenciaAlcance.equals(p.getDependencia().getId()))) {
            return Resultado.error("Ese registro no es de tu dependencia.");
        }
        p.setFoto(null);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);
        return Resultado.exito("Foto quitada.", personalApoyoService.save(p));
    }

    private String validarDependencia(DependenciaDatos d) {
        if (d.nombre() == null || d.nombre().isBlank()) return "El nombre de la dependencia es obligatorio.";
        return null;
    }

    private String validarPersonal(Datos d) {
        if (d.idDependencia() == null) return "La dependencia es obligatoria.";
        if (d.nombre() == null || d.nombre().isBlank()) return "El nombre es obligatorio.";
        if (d.paterno() == null || d.paterno().isBlank()) return "El apellido paterno es obligatorio.";
        if (d.ci() == null || d.ci().isBlank()) return "El C.I. es obligatorio.";
        if (d.rol() == null || d.rol().isBlank()) return "El rol es obligatorio (ej. fotógrafo, azafata, logística).";
        if (d.descripcionTarea() != null && d.descripcionTarea().length() > 200)
            return "La descripción de la tarea no puede pasar de 200 caracteres.";
        return null;
    }

    private void aplicar(PersonalApoyo p, Datos d, Dependencia dep) {
        p.setDependencia(dep);
        p.setNombre(trim(d.nombre()));
        p.setPaterno(trim(d.paterno()));
        p.setMaterno(trim(d.materno()));
        p.setCi(trim(d.ci()));
        // El formulario ya no pide correo ni celular: si no vienen, se conserva lo que habia
        // en vez de borrarlo (en altas quedan nulos, que es correcto: nadie los dio).
        if (d.correo() != null) p.setCorreo(trim(d.correo()));
        if (d.celular() != null) p.setCelular(trim(d.celular()));
        p.setRol(trim(d.rol()));
        // Igual que correo/celular: si no viene, se conserva (el alta anterior no la pedia).
        if (d.descripcionTarea() != null) p.setDescripcionTarea(trim(d.descripcionTarea()));
    }

    private String trim(String s) { return s == null ? null : s.trim(); }
}