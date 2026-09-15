package com.usic.uniFex.controller.api;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.usic.uniFex.Config.CodificadorErrores;
import com.usic.uniFex.model.service.RegistroErroresService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/app/errores")
@RequiredArgsConstructor
@Slf4j
public class RegistroErroresApiController {
    private final RegistroErroresService registros;
    private final Map<Long, long[]> limites = new LinkedHashMap<>();

    @GetMapping("/archivos")
    @PreAuthorize(Roles.ADMINISTRA)
    public Object archivos() throws IOException { return registros.archivos(); }

    @GetMapping("/descargar")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> descargar(@RequestParam(defaultValue = "errores.txt") String archivo) throws IOException {
        try {
            byte[] texto = registros.descargar(archivo);
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .header("Content-Disposition", org.springframework.http.ContentDisposition.attachment()
                            .filename(archivo).build().toString())
                    .contentType(new org.springframework.http.MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                    .body(texto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Archivo invalido");
        } catch (java.nio.file.NoSuchFileException e) {
            return ResponseEntity.status(404).body("El archivo ya no existe");
        }
    }

    @DeleteMapping
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> vaciar(@RequestParam String archivo,
            @RequestParam(defaultValue = "false") boolean confirmar, @AuthenticationPrincipal JwtUser usuario) throws IOException {
        if (!confirmar) return ResponseEntity.badRequest().body(Map.of("mensaje", "Debes confirmar el vaciado"));
        try {
            registros.vaciar(archivo);
            log.info("Archivo de errores {} vaciado por el usuario {} (ID {})", archivo, usuario.username(), usuario.id());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Archivo invalido"));
        } catch (java.nio.file.NoSuchFileException e) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "El archivo ya no existe"));
        }
    }

    @GetMapping
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> listar(@RequestParam(defaultValue = "errores.txt") String archivo,
            @RequestParam(required = false) Long antes, @RequestParam(defaultValue = "") String buscar,
            @RequestParam(defaultValue = "") String usuario) throws IOException {
        try {
            return ResponseEntity.ok(registros.leer(archivo, antes, buscar, usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Consulta de errores invalida"));
        }
    }

    public record ErrorCliente(String mensaje, String detalle, String ruta, String origen) {}

    /** Cualquier usuario autenticado puede informar de SU error; la identidad sale del JWT. */
    @PostMapping("/cliente")
    public ResponseEntity<?> cliente(@AuthenticationPrincipal JwtUser usuario, @RequestBody ErrorCliente error) {
        if (usuario == null) return ResponseEntity.status(401).build();
        if (error.mensaje() == null || error.mensaje().isBlank() || error.mensaje().length() > 2000
                || error.detalle() != null && error.detalle().length() > 6000
                || error.ruta() != null && error.ruta().length() > 500) return ResponseEntity.badRequest().build();
        if (!admitir(usuario.id())) return ResponseEntity.noContent().build();
        String anterior = MDC.get("origen");
        try {
            MDC.put("origen", "APK".equals(error.origen()) ? "CLIENTE APK" : "CLIENTE WEB");
            log.error("Error mostrado al usuario en {}: {}\n{}",
                    CodificadorErrores.limpiar(error.ruta(), 500),
                    CodificadorErrores.limpiar(error.mensaje(), 2000),
                    CodificadorErrores.limpiar(error.detalle(), 6000));
        } finally {
            if (anterior == null) MDC.remove("origen"); else MDC.put("origen", anterior);
        }
        return ResponseEntity.noContent().build();
    }

    private synchronized boolean admitir(Long usuarioId) {
        long ahora = System.currentTimeMillis();
        limites.entrySet().removeIf(e -> ahora - e.getValue()[0] >= 60_000);
        if (!limites.containsKey(usuarioId) && limites.size() >= 1000) return false;
        long[] ventana = limites.computeIfAbsent(usuarioId, k -> new long[]{ahora, 0});
        return ++ventana[1] <= 20;
    }
}
