package com.usic.uniFex.model.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dao.INoticiaDao;
import com.usic.uniFex.model.entity.Edicion;
import com.usic.uniFex.model.entity.Noticia;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CRUD de noticias (V42): el carrusel de novedades de la vista publica, administrable desde el
 * panel. Mismo patron que {@link NochesFexpoService}, con una diferencia: aqui la foto o el
 * video es OBLIGATORIO y llega en la misma peticion que los datos. Asi no puede quedar una
 * noticia a medias (guardada sin medio) si la subida falla.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticiasService {

    /** Cuantas muestra el carrusel publico. */
    public static final int MAXIMO_PUBLICO = 10;

    private static final List<String> EXTENSIONES_FOTO = List.of(".png", ".jpg", ".jpeg", ".webp", ".gif");
    private static final List<String> EXTENSIONES_VIDEO = List.of(".mp4", ".webm");
    private static final int MAX_TITULO = 160;
    private static final int MAX_TEXTO = 2000;

    private final INoticiaDao noticiaDao;
    private final IEdicionDao edicionDao;
    private final FileStorageService almacen;

    public record Datos(LocalDate fecha, Integer dia, String titulo, String texto) {
    }

    public record Resultado(boolean ok, String mensaje, Noticia noticia) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado exito(String m, Noticia n) { return new Resultado(true, m, n); }
    }

    /**
     * Todas las vivas de la edicion activa, de la ultima registrada a la primera: el panel, la
     * vista publica de todas las noticias y lo que se difunde por WebSocket.
     */
    @Transactional(readOnly = true)
    public List<Noticia> listarDeEdicionActiva() {
        Edicion activa = edicionActiva();
        if (activa == null) return List.of();
        return noticiaDao.findByEdicionIdAndEstadoNotOrderByIdDesc(
                activa.getId(), Noticia.REGISTRO_ANULADO);
    }

    /** Las {@value #MAXIMO_PUBLICO} ultimas registradas de la edicion activa (carrusel publico). */
    @Transactional(readOnly = true)
    public List<Noticia> recientesPublicas() {
        Edicion activa = edicionActiva();
        if (activa == null) return List.of();
        return noticiaDao.findTop10ByEdicionIdAndEstadoNotOrderByIdDesc(
                activa.getId(), Noticia.REGISTRO_ANULADO);
    }

    @Transactional
    public Resultado crear(Datos d, MultipartFile archivo, Long actorId) {
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);
        if (archivo == null || archivo.isEmpty()) return Resultado.error("La foto o el video es obligatorio.");
        String tipo = tipoMedio(archivo.getOriginalFilename());
        if (tipo == null) return Resultado.error("Formato no admitido. Usa una foto (PNG, JPG, WEBP, GIF) o un video (MP4, WEBM).");
        Edicion activa = edicionActiva();
        if (activa == null) return Resultado.error("No hay una edicion activa a la que asignarle la noticia.");

        String ruta;
        try {
            ruta = almacen.save(archivo, FileStorageService.Bucket.NOTICIAS, d.titulo());
        } catch (IOException e) {
            log.warn("No se pudo guardar el medio de una noticia nueva", e);
            return Resultado.error("No se pudo guardar el archivo: " + e.getMessage());
        }

        Noticia n = new Noticia();
        n.setEdicion(activa);
        aplicar(n, d);
        n.setMedioArchivo(ruta);
        n.setMedioTipo(tipo);
        n.setEstado(Noticia.REGISTRO_ACTIVO);
        Date ahora = new Date();
        n.setRegistro(ahora);
        n.setModificacion(ahora);
        n.setRegistroIdUsuario(actorId);
        n.setModificacionIdUsuario(actorId);
        return Resultado.exito("Noticia publicada.", noticiaDao.save(n));
    }

    /** Edita los datos y, si llega un archivo, reemplaza la foto o el video. */
    @Transactional
    public Resultado editar(Long id, Datos d, MultipartFile archivo, Long actorId) {
        Noticia n = buscarViva(id);
        if (n == null) return Resultado.error("Noticia no encontrada.");
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);

        if (archivo != null && !archivo.isEmpty()) {
            String tipo = tipoMedio(archivo.getOriginalFilename());
            if (tipo == null) return Resultado.error("Formato no admitido. Usa una foto (PNG, JPG, WEBP, GIF) o un video (MP4, WEBM).");
            try {
                // El archivo anterior se queda en disco, como en NochesFexpoService: si el nuevo
                // sale mal, se puede volver a subir el de antes.
                n.setMedioArchivo(almacen.save(archivo, FileStorageService.Bucket.NOTICIAS, d.titulo()));
            } catch (IOException e) {
                log.warn("No se pudo guardar el medio de la noticia {}", id, e);
                return Resultado.error("No se pudo guardar el archivo: " + e.getMessage());
            }
            n.setMedioTipo(tipo);
        }

        aplicar(n, d);
        n.setModificacion(new Date());
        n.setModificacionIdUsuario(actorId);
        return Resultado.exito("Noticia actualizada.", noticiaDao.save(n));
    }

    /** Baja logica: deja de verse en el panel y en el carrusel, la fila queda. */
    @Transactional
    public Resultado eliminar(Long id, Long actorId) {
        Noticia n = buscarViva(id);
        if (n == null) return Resultado.error("Noticia no encontrada.");
        n.setEstado(Noticia.REGISTRO_ANULADO);
        n.setModificacion(new Date());
        n.setModificacionIdUsuario(actorId);
        noticiaDao.save(n);
        return Resultado.exito("Noticia eliminada.", n);
    }

    private Edicion edicionActiva() {
        return edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
    }

    private Noticia buscarViva(Long id) {
        Noticia n = noticiaDao.findById(id).orElse(null);
        if (n == null || Noticia.REGISTRO_ANULADO.equalsIgnoreCase(n.getEstado())) return null;
        return n;
    }

    private void aplicar(Noticia n, Datos d) {
        n.setFecha(d.fecha());
        n.setDia(d.dia());
        n.setTitulo(d.titulo().trim());
        n.setTexto(d.texto().trim());
    }

    private String validar(Datos d) {
        if (d.fecha() == null) return "La fecha es obligatoria.";
        if (d.dia() == null || d.dia() < Noticia.DIA_MINIMO || d.dia() > Noticia.DIA_MAXIMO) {
            return "Elige el dia de la feria: Dia 1, Dia 2 o Dia 3.";
        }
        if (vacio(d.titulo())) return "El titulo es obligatorio.";
        if (d.titulo().trim().length() > MAX_TITULO) return "El titulo admite hasta " + MAX_TITULO + " caracteres.";
        if (vacio(d.texto())) return "El texto de la noticia es obligatorio.";
        if (d.texto().trim().length() > MAX_TEXTO) return "El texto admite hasta " + MAX_TEXTO + " caracteres.";
        return null;
    }

    /** FOTO o VIDEO segun la extension; null si no es ninguno de los dos (p.ej. un PDF o un MP3). */
    private String tipoMedio(String nombreOriginal) {
        if (nombreOriginal == null) return null;
        String n = nombreOriginal.toLowerCase(Locale.ROOT);
        if (EXTENSIONES_VIDEO.stream().anyMatch(n::endsWith)) return Noticia.MEDIO_VIDEO;
        if (EXTENSIONES_FOTO.stream().anyMatch(n::endsWith)) return Noticia.MEDIO_FOTO;
        return null;
    }

    private boolean vacio(String s) { return s == null || s.isBlank(); }
}
