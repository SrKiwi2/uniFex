package com.usic.uniFex.controller.edicion;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dto.NocheFexpoDTO;
import com.usic.uniFex.model.service.NochesFexpoService;
import com.usic.uniFex.model.service.NochesFexpoService.Datos;
import com.usic.uniFex.model.service.NochesFexpoService.Resultado;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * CRUD de "Noches de FEXPO" para el panel de admin (V26). Contenido de la vista publica
 * (la cartelera de artistas), no relacionado con la venta de casetas -- mismo permiso que
 * el resto de administracion general ({@link Roles#ADMINISTRA}).
 *
 * La vista publica en si NO usa este controlador: lee de {@code GET /api/publico/feria},
 * sin autenticacion (ver {@code FeriaPublicaController}).
 */
@RestController
@RequestMapping("/api/app/noches-fexpo")
@RequiredArgsConstructor
@PreAuthorize(Roles.ADMINISTRA)
public class NochesFexpoApiController {

    private final NochesFexpoService servicio;

    /** Cuerpo de alta/edicion. `fecha` en ISO (yyyy-MM-dd), como manda Vue por defecto. */
    public record NocheRequest(LocalDate fecha, String titulo, String descripcion,
                                String nombreArtista, String color, Integer orden) {
        Datos aDatos() {
            return new Datos(fecha, titulo, descripcion, nombreArtista, color, orden);
        }
    }

    @GetMapping
    public List<NocheFexpoDTO> listar() {
        return servicio.listarDeEdicionActiva().stream().map(NocheFexpoDTO::de).toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody NocheRequest req) {
        return responder(servicio.crear(req.aDatos(), actorId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody NocheRequest req) {
        return responder(servicio.editar(id, req.aDatos(), actorId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        return responder(servicio.eliminar(id, actorId()));
    }

    @PostMapping(value = "/{id}/medio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirMedio(
            @PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
        return responder(servicio.subirMedio(id, archivo, actorId()));
    }

    @DeleteMapping("/{id}/medio")
    public ResponseEntity<Map<String, Object>> quitarMedio(@PathVariable Long id) {
        return responder(servicio.quitarMedio(id, actorId()));
    }

    private ResponseEntity<Map<String, Object>> responder(Resultado r) {
        // LinkedHashMap y no Map.of: `noche` es null cuando falla, y Map.of no admite null.
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("noche", r.noche() != null ? NocheFexpoDTO.de(r.noche()) : null);
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }

    private Long actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }
}
