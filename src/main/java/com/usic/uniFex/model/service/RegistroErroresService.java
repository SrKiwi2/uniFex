package com.usic.uniFex.model.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Lee un tramo acotado del archivo elegido; nunca carga todo el historial en memoria. */
@Service
public class RegistroErroresService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final Path directorio;
    private final ObjectMapper json;

    public RegistroErroresService(@Value("${unifex.logs.directorio:logs}") String directorio, ObjectMapper json) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        this.json = json;
    }

    public List<String> archivos() throws IOException {
        if (!Files.exists(directorio)) return List.of();
        try (var rutas = Files.list(directorio)) {
            return rutas.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .map(p -> p.getFileName().toString()).filter(this::nombreValido)
                    .sorted(Comparator.reverseOrder()).toList();
        }
    }

    private boolean nombreValido(String nombre) {
        return nombre != null && nombre.matches("errores(?:\\.\\d{4}-\\d{2}-\\d{2}\\.\\d+)?\\.txt");
    }

    public record Resultado(List<Map<String, Object>> errores, long anterior, String archivo) {}

    private Object bloqueoArchivo() {
        var contexto = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        var appender = contexto.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("ERRORES");
        return appender instanceof com.usic.uniFex.Config.ArchivoErroresAppender ? appender : this;
    }

    private Path archivoSeguro(String archivo) throws IOException {
        if (!nombreValido(archivo)) throw new IllegalArgumentException("Archivo invalido");
        Path ruta = directorio.resolve(archivo).normalize();
        if (!ruta.getParent().equals(directorio)) throw new IllegalArgumentException("Archivo invalido");
        if (!Files.isRegularFile(ruta, LinkOption.NOFOLLOW_LINKS)) throw new java.nio.file.NoSuchFileException(archivo);
        return ruta;
    }

    public byte[] descargar(String archivo) throws IOException {
        synchronized (bloqueoArchivo()) {
            try (var entrada = Files.newInputStream(archivoSeguro(archivo), LinkOption.NOFOLLOW_LINKS)) {
                byte[] datos = entrada.readNBytes(16 * 1024 * 1024 + 1);
                if (datos.length > 16 * 1024 * 1024) throw new IOException("Archivo demasiado grande para descargar");
                return datos;
            }
        }
    }

    /** Trunca el archivo sin eliminarlo: el appender puede seguir escribiendo en el mismo descriptor. */
    public void vaciar(String archivo) throws IOException {
        synchronized (bloqueoArchivo()) {
            try (var canal = Files.newByteChannel(archivoSeguro(archivo), StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                canal.truncate(0);
            }
        }
    }

    public Resultado leer(String archivo, Long antes, String buscar, String usuario) throws IOException {
        if (!nombreValido(archivo) || antes != null && antes < 0) throw new IllegalArgumentException("Consulta invalida");
        Path ruta = directorio.resolve(archivo);
        if (!Files.exists(ruta, LinkOption.NOFOLLOW_LINKS)) return new Resultado(List.of(), 0, archivo);
        if (!Files.isRegularFile(ruta, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Archivo invalido");
        try (SeekableByteChannel canal = Files.newByteChannel(ruta, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long fin = antes == null ? canal.size() : Math.min(antes, canal.size());
            long inicio = Math.max(0, fin - MAX_BYTES);
            canal.position(inicio);
            ByteBuffer buffer = ByteBuffer.allocate((int) (fin - inicio));
            while (buffer.hasRemaining() && canal.read(buffer) > 0) { /* lectura acotada */ }
            byte[] bytes = buffer.array();
            int desde = 0;
            if (inicio > 0) {
                while (desde < buffer.position() && bytes[desde] != '\n') desde++;
                desde++;
            }
            int hasta = buffer.position();
            while (hasta > desde && bytes[hasta - 1] != '\n') hasta--;
            if (desde >= hasta) return new Resultado(List.of(), inicio, archivo);
            String texto = new String(bytes, desde, hasta - desde, StandardCharsets.UTF_8);
            String[] lineas = texto.split("\n");
            var encontrados = new ArrayList<Map<String, Object>>();
            String filtro = normalizar(buscar), filtroUsuario = normalizar(usuario);
            long cursor = inicio + hasta;
            for (int i = lineas.length - 1; i >= 0; i--) {
                cursor -= lineas[i].getBytes(StandardCharsets.UTF_8).length + 1;
                try {
                    Map<String, Object> registro = json.readValue(lineas[i], new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    if (normalizar(String.valueOf(registro.get("usuario"))).contains(filtroUsuario)
                            && normalizar(lineas[i]).contains(filtro)) encontrados.add(registro);
                } catch (com.fasterxml.jackson.core.JsonProcessingException ignorada) { /* linea incompleta o antigua */ }
                if (encontrados.size() == 100) return new Resultado(encontrados, cursor, archivo);
            }
            return new Resultado(encontrados, inicio == 0 ? 0 : inicio + desde, archivo);
        }
    }

    private static String normalizar(String valor) { return valor == null ? "" : valor.toLowerCase(Locale.ROOT); }
}
