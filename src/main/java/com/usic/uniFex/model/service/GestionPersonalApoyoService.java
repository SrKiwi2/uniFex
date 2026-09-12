package com.usic.uniFex.model.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.IDependenciaService;
import com.usic.uniFex.model.IService.IPersonalApoyoService;
import com.usic.uniFex.model.entity.Dependencia;
import com.usic.uniFex.model.entity.PersonalApoyo;

import lombok.RequiredArgsConstructor;

/**
 * Gestión del personal de apoyo de la feria (fotógrafos, azafatas, logística, etc.).
 * Módulo aislado: no toca la tabla persona ni usuario existentes.
 */
@Service
@RequiredArgsConstructor
public class GestionPersonalApoyoService {

    public static final String ACTIVO = "A";
    public static final String ELIMINADO = "X";

    private final IPersonalApoyoService personalApoyoService;
    private final IDependenciaService dependenciaService;

    public record Datos(Long idDependencia, String nombre, String paterno, String materno,
                        String ci, String correo, String celular, String rol) {
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
        String falta = validarPersonal(d);
        if (falta != null) return Resultado.error(falta);

        Dependencia dep = dependenciaService.findById(d.idDependencia());
        if (dep == null || ELIMINADO.equalsIgnoreCase(dep.getEstado())) {
            return Resultado.error("Dependencia no encontrada.");
        }
        if (personalApoyoService.findByCi(d.ci()).isPresent()) {
            return Resultado.error("Ya existe personal con ese C.I.");
        }

        PersonalApoyo p = new PersonalApoyo();
        aplicar(p, d, dep);
        p.setEstado(ACTIVO);
        p.setRegistro(new Date());
        p.setModificacion(new Date());
        p.setRegistroIdUsuario(actorId);
        p.setModificacionIdUsuario(actorId);

        return Resultado.exito("Personal creado.", personalApoyoService.save(p));
    }

    @Transactional
    public Resultado editarPersonal(Long id, Datos d, Long actorId) {
        PersonalApoyo p = personalApoyoService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) {
            return Resultado.error("Personal no encontrado.");
        }
        String falta = validarPersonal(d);
        if (falta != null) return Resultado.error(falta);

        Optional<PersonalApoyo> existente = personalApoyoService.findByCi(d.ci());
        if (existente.isPresent() && !existente.get().getId().equals(id)) {
            return Resultado.error("Ya existe otro personal con ese C.I.");
        }

        Dependencia dep = dependenciaService.findById(d.idDependencia());
        if (dep == null || ELIMINADO.equalsIgnoreCase(dep.getEstado())) {
            return Resultado.error("Dependencia no encontrada.");
        }

        aplicar(p, d, dep);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);

        return Resultado.exito("Personal actualizado.", personalApoyoService.save(p));
    }

    @Transactional
    public Resultado eliminarPersonal(Long id, Long actorId) {
        PersonalApoyo p = personalApoyoService.findById(id);
        if (p == null || ELIMINADO.equalsIgnoreCase(p.getEstado())) {
            return Resultado.error("Personal no encontrado.");
        }

        p.setEstado(ELIMINADO);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(actorId);
        return Resultado.exito("Personal eliminado.", personalApoyoService.save(p));
    }

    // ===== helpers =====

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
        return null;
    }

    private void aplicar(PersonalApoyo p, Datos d, Dependencia dep) {
        p.setDependencia(dep);
        p.setNombre(trim(d.nombre()));
        p.setPaterno(trim(d.paterno()));
        p.setMaterno(trim(d.materno()));
        p.setCi(trim(d.ci()));
        p.setCorreo(trim(d.correo()));
        p.setCelular(trim(d.celular()));
        p.setRol(trim(d.rol()));
    }

    private String trim(String s) { return s == null ? null : s.trim(); }
}