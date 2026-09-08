package com.usic.uniFex.model.service;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dao.IPersonasDao;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.dto.ResponsableFotoDTO;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Las fotos de los responsables de una venta: lo que despues va impreso en su credencial.
 *
 * <h2>Por que no hubo que tocar el esquema</h2>
 *
 * La columna {@code persona.foto} ya existe y ya la usa el registro Thymeleaf ({@code /guardar}),
 * que sube las fotos de los dos responsables al bucket {@code responsables/} y guarda la ruta
 * relativa. Lo que faltaba no era donde guardarla, sino poder <b>reunirla despues</b>: en la
 * feria muchas veces no se tiene la foto en el momento de vender —el cliente vuelve con ella,
 * o cambia el ayudante—, y hasta ahora la unica forma de agregarla era rehacer el registro.
 *
 * La foto vive en {@code persona} y no en {@code responsable} porque es la cara de alguien:
 * si esa persona vuelve el anio siguiente o responde por otra entidad, sirve la misma.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponsableFotoService {

    private final IResponsableDao responsableDao;
    private final IPersonasDao personaDao;
    private final IInscripcionDao inscripcionDao;
    private final FileStorageService almacen;

    public record Resultado(boolean ok, String mensaje, ResponsableFotoDTO responsable) {
    }

    /** Los responsables de la venta, con el estado de su foto. */
    @Transactional(readOnly = true)
    public List<ResponsableFotoDTO> listar(Long inscripcionId) {
        Inscripcion ins = inscripcionDao.findById(inscripcionId).orElse(null);
        if (ins == null || ins.getEntidad() == null) return List.of();
        return responsableDao.findVigentesDeEntidad(ins.getEntidad().getId()).stream()
                .map(ResponsableFotoDTO::de)
                .toList();
    }

    /** ¿Estan todas las fotos? Es lo que decide si se puede emitir la credencial. */
    @Transactional(readOnly = true)
    public boolean fotosCompletas(Long inscripcionId) {
        List<ResponsableFotoDTO> rs = listar(inscripcionId);
        return !rs.isEmpty() && rs.stream().allMatch(ResponsableFotoDTO::tieneFoto);
    }

    /**
     * Guarda o reemplaza la foto de un responsable de esta venta.
     *
     * Se exige que el responsable pertenezca a la entidad de la inscripcion indicada: sin esa
     * comprobacion, cualquiera con una venta propia podria cambiarle la foto al responsable de
     * otro vendedor pasando su id, porque el permiso se valida sobre la inscripcion.
     */
    @Transactional
    public Resultado guardar(Long inscripcionId, Long responsableId, MultipartFile archivo, Long usuarioId) {
        if (archivo == null || archivo.isEmpty()) {
            return new Resultado(false, "No llego ninguna imagen", null);
        }
        Inscripcion ins = inscripcionDao.findById(inscripcionId).orElse(null);
        if (ins == null || ins.getEntidad() == null) {
            return new Resultado(false, "La venta no existe", null);
        }
        Responsable r = responsableDao.findById(responsableId).orElse(null);
        if (r == null || r.getEntidad() == null
                || !r.getEntidad().getId().equals(ins.getEntidad().getId())) {
            return new Resultado(false, "Ese responsable no es de esta venta", null);
        }
        Persona p = r.getPersona();
        if (p == null) {
            return new Resultado(false, "El responsable no tiene persona asociada", null);
        }

        String ruta;
        try {
            ruta = almacen.save(archivo, FileStorageService.Bucket.RESPONSABLES,
                    p.getNombre() + " " + p.getPaterno());
        } catch (IOException e) {
            log.warn("No se pudo guardar la foto del responsable {}", responsableId, e);
            return new Resultado(false, "No se pudo guardar la imagen: " + e.getMessage(), null);
        }

        // La anterior NO se borra: si la nueva sale mal (movida, a contraluz), la vieja sigue
        // en disco. Pesan poco y son una por persona.
        p.setFoto(ruta);
        // La auditoria de JPA esta apagada en este proyecto: hay que sellar a mano o queda
        // sin rastro de quien cambio la foto.
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(usuarioId);
        personaDao.save(p);

        log.info("Foto del responsable {} (persona {}) actualizada por el usuario {}",
                responsableId, p.getId(), usuarioId);
        return new Resultado(true, "Foto guardada", ResponsableFotoDTO.de(r));
    }

    /** Quita la foto. No borra el archivo: solo deja de referenciarlo. */
    @Transactional
    public Resultado quitar(Long inscripcionId, Long responsableId, Long usuarioId) {
        Inscripcion ins = inscripcionDao.findById(inscripcionId).orElse(null);
        Responsable r = responsableDao.findById(responsableId).orElse(null);
        if (ins == null || r == null || r.getEntidad() == null || ins.getEntidad() == null
                || !r.getEntidad().getId().equals(ins.getEntidad().getId())) {
            return new Resultado(false, "Ese responsable no es de esta venta", null);
        }
        Persona p = r.getPersona();
        if (p == null) return new Resultado(false, "El responsable no tiene persona asociada", null);

        p.setFoto(null);
        p.setModificacion(new Date());
        p.setModificacionIdUsuario(usuarioId);
        personaDao.save(p);
        return new Resultado(true, "Foto quitada", ResponsableFotoDTO.de(r));
    }
}
