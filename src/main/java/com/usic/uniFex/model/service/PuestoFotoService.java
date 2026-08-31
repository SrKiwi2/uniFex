package com.usic.uniFex.model.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dao.IPuestoFotoDao;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.PuestoFoto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fotos de las casetas: como se ven de verdad en el lugar.
 *
 * Quien las sube es la administracion desde el Editor (es trabajo de montaje, igual que
 * colocar las casetas en el plano); quien las consume es el vendedor en el mapa, delante
 * del cliente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuestoFotoService {

    /** Tope por caseta: mas fotos no ayudan a decidir y solo pesan en el movil. */
    public static final int MAX_POR_CASETA = 6;

    private final IPuestoFotoDao fotoDao;
    private final IPuestoDao puestoDao;
    private final FileStorageService storage;

    public record Resultado(boolean ok, String mensaje, List<Long> puestosAfectados) {
        static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, List.of());
        }
    }

    @Transactional(readOnly = true)
    public List<PuestoFoto> fotosDe(Long puestoId) {
        return fotoDao.activasDe(puestoId);
    }

    /** Ids de casetas con al menos una foto, para que el mapa marque cuales se pueden enseñar. */
    @Transactional(readOnly = true)
    public List<Long> idsConFoto() {
        return fotoDao.idsDePuestosConFoto();
    }

    /**
     * Sube una foto y la asocia a UNA O VARIAS casetas.
     *
     * Lo de varias no es un capricho: en una feria hay filas enteras de casetas identicas, y
     * obligar a subir la misma imagen 40 veces significa que nadie lo hara y la funcionalidad
     * quedara vacia. El archivo se guarda **una sola vez** y las filas comparten la ruta.
     *
     * Si una caseta ya llego al tope, se la salta y sigue con las demas: es mas util que
     * abortar el lote entero.
     */
    @Transactional
    public Resultado subir(List<Long> puestoIds, MultipartFile archivo, String descripcion, Long usuarioId) {
        if (puestoIds == null || puestoIds.isEmpty()) return Resultado.error("No se indico ninguna caseta");
        if (archivo == null || archivo.isEmpty()) return Resultado.error("No se recibio ningun archivo");

        List<Puesto> casetas = puestoDao.findAllById(puestoIds);
        if (casetas.isEmpty()) return Resultado.error("Las casetas no existen");

        String ruta;
        try {
            Puesto primera = casetas.get(0);
            String slug = (primera.getCategoria() != null ? primera.getCategoria().getNombre() + " " : "")
                    + primera.getCodigo();
            ruta = storage.save(archivo, FileStorageService.Bucket.PUESTOS, slug);
        } catch (IOException e) {
            log.warn("No se pudo guardar la foto de caseta: {}", e.getMessage());
            return Resultado.error("No se pudo guardar el archivo: " + e.getMessage());
        }

        Date ahora = new Date();
        List<Long> afectados = new ArrayList<>();
        int llenas = 0;
        for (Puesto p : casetas) {
            long ya = fotoDao.contarActivasDe(p.getId());
            if (ya >= MAX_POR_CASETA) { llenas++; continue; }

            PuestoFoto f = new PuestoFoto();
            f.setPuesto(p);
            f.setRuta(ruta);
            f.setDescripcion(descripcion);
            f.setOrden((int) ya);
            f.setEstado(PuestoFoto.ACTIVA);
            f.setRegistro(ahora);
            f.setRegistroIdUsuario(usuarioId);
            f.setModificacion(ahora);
            f.setModificacionIdUsuario(usuarioId);
            fotoDao.save(f);
            afectados.add(p.getId());
        }

        String mensaje = "Foto agregada a " + afectados.size() + " caseta(s)"
                + (llenas > 0 ? "; " + llenas + " ya tenian el maximo de " + MAX_POR_CASETA : "");
        log.info("Fotos de caseta: {} (usuario={})", mensaje, usuarioId);
        return new Resultado(!afectados.isEmpty(), mensaje, afectados);
    }

    /**
     * Baja logica de una foto.
     *
     * El archivo del disco NO se borra: la misma imagen puede estar compartida por varias
     * casetas (ver {@link #subir}), asi que borrarla dejaria a las otras sin foto. Recuperar
     * espacio es una tarea de mantenimiento aparte.
     */
    @Transactional
    public boolean borrar(Long fotoId) {
        return fotoDao.marcarBorrada(fotoId) > 0;
    }
}
