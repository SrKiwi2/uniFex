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
import com.usic.uniFex.model.dao.INocheFexpoDao;
import com.usic.uniFex.model.entity.Edicion;
import com.usic.uniFex.model.entity.NocheFexpo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CRUD de "Noches de FEXPO" (V26): la cartelera de artistas de la vista publica, administrable
 * desde el panel en vez de hardcodeada en {@code FeriaPublica.vue}.
 *
 * Extensiones de video reconocidas aqui (no en {@link FileStorageService}, que solo sabe de
 * buckets y extensiones permitidas en general).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NochesFexpoService {

    private static final List<String> EXTENSIONES_VIDEO = List.of(".mp4", ".webm");

    private final INocheFexpoDao nocheDao;
    private final IEdicionDao edicionDao;
    private final FileStorageService almacen;

    public record Datos(LocalDate fecha, String titulo, String descripcion,
                         String nombreArtista, String color, Integer orden) {
    }

    public record Resultado(boolean ok, String mensaje, NocheFexpo noche) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado exito(String m, NocheFexpo n) { return new Resultado(true, m, n); }
    }

    /** Las noches vivas de la edicion activa, en orden de calendario. Vacia si no hay edicion activa. */
    @Transactional(readOnly = true)
    public List<NocheFexpo> listarDeEdicionActiva() {
        Edicion activa = edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
        if (activa == null) return List.of();
        return nocheDao.findByEdicionIdAndEstadoNotOrderByFechaAscOrdenAsc(
                activa.getId(), NocheFexpo.REGISTRO_ANULADO);
    }

    @Transactional
    public Resultado crear(Datos d, Long actorId) {
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);
        Edicion activa = edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
        if (activa == null) return Resultado.error("No hay una edicion activa a la que asignarle la noche.");

        NocheFexpo n = new NocheFexpo();
        n.setEdicion(activa);
        aplicar(n, d);
        n.setEstado(NocheFexpo.REGISTRO_ACTIVO);
        Date ahora = new Date();
        n.setRegistro(ahora);
        n.setModificacion(ahora);
        n.setRegistroIdUsuario(actorId);
        n.setModificacionIdUsuario(actorId);
        return Resultado.exito("Noche creada.", nocheDao.save(n));
    }

    @Transactional
    public Resultado editar(Long id, Datos d, Long actorId) {
        NocheFexpo n = buscarViva(id);
        if (n == null) return Resultado.error("Noche no encontrada.");
        String falta = validar(d);
        if (falta != null) return Resultado.error(falta);

        aplicar(n, d);
        n.setModificacion(new Date());
        n.setModificacionIdUsuario(actorId);
        return Resultado.exito("Noche actualizada.", nocheDao.save(n));
    }

    /** Baja logica: no se borra la fila para no perder la referencia si algo la usa despues. */
    @Transactional
    public Resultado eliminar(Long id, Long actorId) {
        NocheFexpo n = buscarViva(id);
        if (n == null) return Resultado.error("Noche no encontrada.");
        n.setEstado(NocheFexpo.REGISTRO_ANULADO);
        n.setModificacion(new Date());
        n.setModificacionIdUsuario(actorId);
        nocheDao.save(n);
        return Resultado.exito("Noche eliminada.", n);
    }

    /**
     * Reemplaza la foto/video de fondo de una noche. El tipo (FOTO/VIDEO) se decide por la
     * extension del archivo, no se le pregunta al admin -- una cosa menos que pueda contradecir
     * al archivo real.
     */
    @Transactional
    public Resultado subirMedio(Long id, MultipartFile archivo, Long actorId) {
        NocheFexpo n = buscarViva(id);
        if (n == null) return Resultado.error("Noche no encontrada.");
        if (archivo == null || archivo.isEmpty()) return Resultado.error("No llego ningun archivo.");

        String ruta;
        try {
            ruta = almacen.save(archivo, FileStorageService.Bucket.NOCHES,
                    n.getTitulo() != null ? n.getTitulo() : "noche-" + n.getId());
        } catch (IOException e) {
            log.warn("No se pudo guardar el medio de la noche {}", id, e);
            return Resultado.error("No se pudo guardar el archivo: " + e.getMessage());
        }

        // El archivo anterior no se borra a proposito, igual que el plano (PlanoService): si
        // el nuevo sale mal, siempre se puede volver a subir el de antes sin haberlo perdido.
        n.setMedioArchivo(ruta);
        n.setMedioTipo(esVideo(archivo.getOriginalFilename()) ? NocheFexpo.MEDIO_VIDEO : NocheFexpo.MEDIO_FOTO);
        n.setModificacion(new Date());
        n.setModificacionIdUsuario(actorId);
        return Resultado.exito("Medio actualizado.", nocheDao.save(n));
    }

    /** Quita la foto/video de fondo, vuelve a la silueta de "por revelar" en cuanto a medio. */
    @Transactional
    public Resultado quitarMedio(Long id, Long actorId) {
        NocheFexpo n = buscarViva(id);
        if (n == null) return Resultado.error("Noche no encontrada.");
        n.setMedioArchivo(null);
        n.setMedioTipo(null);
        n.setModificacion(new Date());
        n.setModificacionIdUsuario(actorId);
        return Resultado.exito("Medio quitado.", nocheDao.save(n));
    }

    private NocheFexpo buscarViva(Long id) {
        NocheFexpo n = nocheDao.findById(id).orElse(null);
        if (n == null || NocheFexpo.REGISTRO_ANULADO.equalsIgnoreCase(n.getEstado())) return null;
        return n;
    }

    private void aplicar(NocheFexpo n, Datos d) {
        n.setFecha(d.fecha());
        n.setTitulo(d.titulo().trim());
        n.setDescripcion(vacio(d.descripcion()) ? null : d.descripcion().trim());
        n.setNombreArtista(vacio(d.nombreArtista()) ? null : d.nombreArtista().trim());
        n.setColor(vacio(d.color()) ? null : d.color().trim());
        n.setOrden(d.orden() != null ? d.orden() : 0);
    }

    private String validar(Datos d) {
        if (d.fecha() == null) return "La fecha es obligatoria.";
        if (vacio(d.titulo())) return "El titulo es obligatorio.";
        return null;
    }

    private boolean esVideo(String nombreOriginal) {
        if (nombreOriginal == null) return false;
        String n = nombreOriginal.toLowerCase(Locale.ROOT);
        return EXTENSIONES_VIDEO.stream().anyMatch(n::endsWith);
    }

    private boolean vacio(String s) { return s == null || s.isBlank(); }
}
