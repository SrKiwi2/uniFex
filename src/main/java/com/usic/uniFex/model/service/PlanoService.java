package com.usic.uniFex.model.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dto.PlanoDTO;
import com.usic.uniFex.model.entity.Edicion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Plano de la feria por edicion (V13).
 *
 * Existe para que cambiar el plano NO obligue a recompilar el APK: la imagen vive en el
 * servidor y la SPA la pide al abrir el mapa. Antes viajaba dentro del bundle, asi que un
 * plano nuevo significaba reinstalar la app en cada telefono.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanoService {

    private final IEdicionDao edicionDao;
    private final FileStorageService almacen;

    /** Resultado de un reemplazo: `ok` false trae el motivo listo para mostrar. */
    public record Resultado(boolean ok, String mensaje, PlanoDTO plano) {
    }

    /** El plano de la edicion activa, o el de respaldo si todavia no subieron ninguno. */
    @Transactional(readOnly = true)
    public PlanoDTO activo() {
        return edicionDao.findFirstByActivaTrueOrderByAnioDesc()
                .map(PlanoDTO::de)
                .orElseGet(PlanoDTO::respaldo);
    }

    /**
     * Reemplaza el plano de la edicion activa.
     *
     * Las medidas se leen de la imagen y no se piden al cliente: son la proporcion con la que
     * el visor encuadra, y un numero mal tecleado deforma el plano para todos. Si no se pueden
     * leer, se rechaza — es preferible eso a guardar un plano que luego se dibuja torcido.
     */
    @Transactional
    public Resultado reemplazar(MultipartFile archivo, Long usuarioId) {
        if (archivo == null || archivo.isEmpty()) {
            return new Resultado(false, "No llego ninguna imagen", null);
        }
        Edicion edicion = edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
        if (edicion == null) {
            return new Resultado(false, "No hay una edicion activa a la que asignarle el plano", null);
        }

        int ancho;
        int alto;
        try (InputStream in = archivo.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                return new Resultado(false,
                        "No se pudo leer la imagen. Usa PNG o JPG (el formato WEBP no se puede medir aqui).", null);
            }
            ancho = img.getWidth();
            alto = img.getHeight();
        } catch (IOException e) {
            log.warn("No se pudo medir el plano subido", e);
            return new Resultado(false, "No se pudo leer la imagen: " + e.getMessage(), null);
        }

        String ruta;
        try {
            ruta = almacen.save(archivo, FileStorageService.Bucket.PLANOS, "plano-" + edicion.getAnio());
        } catch (IOException e) {
            log.warn("No se pudo guardar el plano", e);
            return new Resultado(false, "No se pudo guardar el archivo: " + e.getMessage(), null);
        }

        // El archivo anterior NO se borra a proposito: si el plano nuevo sale mal, el viejo
        // sigue en disco y se puede volver a el. Ocupan poco y son uno por edicion.
        edicion.setPlanoArchivo(ruta);
        edicion.setPlanoAncho(ancho);
        edicion.setPlanoAlto(alto);
        edicion.setPlanoVersion((edicion.getPlanoVersion() == null ? 0 : edicion.getPlanoVersion()) + 1);
        edicion.setPlanoSubidoEn(LocalDateTime.now());
        edicionDao.save(edicion);

        log.info("Plano de la edicion {} reemplazado por el usuario {}: {} ({}x{}), version {}",
                edicion.getId(), usuarioId, ruta, ancho, alto, edicion.getPlanoVersion());
        return new Resultado(true, "Plano actualizado (" + ancho + "x" + alto + ")", PlanoDTO.de(edicion));
    }
}
