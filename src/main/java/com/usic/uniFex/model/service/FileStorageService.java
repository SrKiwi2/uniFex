package com.usic.uniFex.model.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileStorageService {
    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    // Extensiones/mime permitidos (ajusta si quieres PDF en comprobante)
    private static final Set<String> ALLOWED_EXT = Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif", ".pdf");
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf"
    );

    public enum Bucket {
        COMPROBANTES("comprobantes"),
        RESPONSABLES("responsables"),
        /** Fotos de como se ve una caseta en el lugar (las enseña el vendedor al cliente). */
        PUESTOS("puestos");

        private final String dir;
        Bucket(String dir) { this.dir = dir; }
        public String dir() { return dir; }
    }

    /**
     * Guarda un archivo con nombre seguro y partición por año/mes.
     * @return ruta relativa (p.ej. "comprobantes/2025/09/uuid-ENTIDAD-X.png")
     */
    public String save(MultipartFile file, Bucket bucket, String baseNameForSlug) throws IOException {
        if (file == null || file.isEmpty()) return null;

        // 1) Validar mimetype (best-effort; depende del cliente)
        String contentType = file.getContentType();
        if (contentType != null && ALLOWED_MIME.stream().noneMatch(contentType::equalsIgnoreCase)) {
            throw new IOException("Tipo de archivo no permitido: " + contentType);
        }

        // 2) Obtener extensión segura
        String ext = safeExtension(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IOException("Extensión no permitida: " + ext);
        }

        // 3) Estructura yyyy/MM
        LocalDate today = LocalDate.now();
        Path base = Paths.get(uploadRoot, bucket.dir(), String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue()))
                .toAbsolutePath().normalize();

        // 4) Crear carpeta
        Files.createDirectories(base);

        // 5) Nombre único + “slug” legible
        String slug = slugify(baseNameForSlug);
        String unique = UUID.randomUUID().toString();
        String filename = unique + (slug.isEmpty() ? "" : "-" + slug) + ext;

        Path destino = base.resolve(filename).normalize();
        // 6) Guardar
        file.transferTo(destino.toFile());

        // 7) Devolver ruta relativa para guardar en DB
        String relative = bucket.dir() + "/" + today.getYear() + "/" +
                String.format("%02d", today.getMonthValue()) + "/" + filename;

        log.info("[STORAGE] Saved {} -> {}", bucket, destino);
        return relative;
    }

    private String safeExtension(String originalName) {
        if (originalName == null) return ".jpg";
        int dot = originalName.lastIndexOf('.');
        String ext = (dot > -1 && dot < originalName.length() - 1)
                ? originalName.substring(dot).toLowerCase()
                : ".jpg";
        // normaliza .jpeg -> .jpg si quieres
        if (".jpeg".equals(ext)) return ".jpg";
        return ext;
    }

    private String slugify(String input) {
        if (input == null) return "";
        String slug = input
                .trim()
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-|-$)", "")
                .toUpperCase();
        return slug.length() > 60 ? slug.substring(0, 60) : slug;
    }
}
