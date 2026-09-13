package com.usic.uniFex.model.service;

import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dao.IPersonasDao;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Corregir los datos de una venta ya registrada.
 *
 * <h2>Por que hace falta</h2>
 * Una venta se registra de pie, delante del cliente y con prisa: se escribe mal un apellido, el
 * C.I. lleva un digito de menos, el celular es el viejo. Hasta ahora eso no tenia arreglo desde
 * la aplicacion — habia que ir a la base — y esos mismos datos son los que se imprimen en la
 * credencial y los que se miran en la puerta.
 *
 * <h2>Que se puede cambiar y que no</h2>
 * Se cambian los DATOS DESCRIPTIVOS: nombre de la entidad, NIT, rubro, y los del responsable
 * legal y de cada responsable. <b>No se tocan aqui las casetas, ni el importe, ni el pago</b>:
 * eso no es corregir un dato, es rehacer la venta, y mover una caseta tiene que pasar por la
 * maquina de estados que impide venderla dos veces.
 *
 * <h2>Quien</h2>
 * Lo decide el controlador con {@code comprobarAcceso}: el vendedor que registro la venta,
 * administracion, o quien acredita. La comprobacion de que el responsable pertenece de verdad a
 * esta venta se hace aqui y no alli, porque es una regla del dominio: sin ella, cualquiera con
 * una venta propia podria editar al responsable de otro vendedor pasando su id.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EdicionVentaService {

    private final IInscripcionDao inscripcionDao;
    private final IResponsableDao responsableDao;
    private final IPersonasDao personaDao;

    public record Resultado(boolean ok, String mensaje) {
        static Resultado error(String m) { return new Resultado(false, m); }
    }

    /** Datos de la entidad y de su responsable legal. Un campo nulo se deja como estaba. */
    public record DatosEntidad(String entidadNombre, String nit, String descripcion,
                               String representanteLegal, String ciRepresentante,
                               String celularRepresentante) {}

    /** Datos de una persona responsable. Un campo nulo se deja como estaba. */
    public record DatosResponsable(String nombre, String paterno, String materno,
                                   String ci, String celular) {}

    @Transactional
    public Resultado editarEntidad(Long inscripcionId, DatosEntidad d, Long usuarioId) {
        Inscripcion i = inscripcionDao.findById(inscripcionId).orElse(null);
        if (i == null || i.getEntidad() == null) return Resultado.error("La venta no existe");
        if (d == null) return Resultado.error("No llegaron datos");

        Entidad e = i.getEntidad();
        // El nombre es lo unico que no puede quedar vacio: es como se llama a esta venta en
        // todos los listados, y una fila sin nombre no hay forma de encontrarla despues.
        String nombre = mayus(d.entidadNombre());
        if (vacio(nombre) && vacio(e.getNombre())) {
            return Resultado.error("El nombre de la entidad es obligatorio");
        }
        if (!vacio(nombre)) e.setNombre(nombre);
        if (d.nit() != null) e.setNit(d.nit().trim());
        if (d.descripcion() != null) e.setDescripcion(mayus(d.descripcion()));
        if (d.representanteLegal() != null) e.setRepresentanteLegal(mayus(d.representanteLegal()));
        if (d.ciRepresentante() != null) e.setCiRepresentante(d.ciRepresentante().trim());
        if (d.celularRepresentante() != null) e.setCelularRepresentante(d.celularRepresentante().trim());

        // La auditoria de JPA esta APAGADA en este proyecto pese a las anotaciones: si no se
        // escriben a mano, estos campos quedan como estaban y no hay rastro de quien corrigio.
        i.setModificacion(new Date());
        i.setModificacionIdUsuario(usuarioId);
        inscripcionDao.save(i);
        log.info("Datos de la entidad de la inscripcion {} corregidos por el usuario {}",
                inscripcionId, usuarioId);
        return new Resultado(true, "Datos de la entidad actualizados");
    }

    @Transactional
    public Resultado editarResponsable(Long inscripcionId, Long responsableId,
                                       DatosResponsable d, Long usuarioId) {
        Inscripcion i = inscripcionDao.findById(inscripcionId).orElse(null);
        if (i == null || i.getEntidad() == null) return Resultado.error("La venta no existe");
        if (d == null) return Resultado.error("No llegaron datos");

        Responsable r = responsableDao.findById(responsableId).orElse(null);
        // La pertenencia se comprueba SIEMPRE, aunque quien llama tenga permiso sobre la venta:
        // el permiso se valida sobre la inscripcion y el id del responsable lo escribe el
        // cliente. Es la misma puerta que cierra ResponsableFotoService al subir una foto.
        if (r == null || r.getEntidad() == null
                || !r.getEntidad().getId().equals(i.getEntidad().getId())) {
            return Resultado.error("Ese responsable no es de esta venta");
        }
        Persona p = r.getPersona();
        if (p == null) return Resultado.error("El responsable no tiene datos de persona");

        String nombre = mayus(d.nombre());
        if (vacio(nombre) && vacio(p.getNombre())) {
            return Resultado.error("El nombre del responsable es obligatorio");
        }
        if (!vacio(nombre)) p.setNombre(nombre);
        if (d.paterno() != null) p.setPaterno(mayus(d.paterno()));
        if (d.materno() != null) p.setMaterno(mayus(d.materno()));
        if (d.ci() != null && !d.ci().isBlank()) p.setCi(d.ci().trim());
        if (d.celular() != null) p.setCelular(d.celular().trim());

        personaDao.save(p);
        i.setModificacion(new Date());
        i.setModificacionIdUsuario(usuarioId);
        inscripcionDao.save(i);
        log.info("Responsable {} de la inscripcion {} corregido por el usuario {}",
                responsableId, inscripcionId, usuarioId);
        return new Resultado(true, "Datos del responsable actualizados");
    }

    /** El resto del sistema guarda nombres en mayusculas; corregir uno no puede romper eso. */
    private static String mayus(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
