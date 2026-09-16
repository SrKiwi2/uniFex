package com.usic.uniFex.controller.edicion;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dto.NoticiaDTO;
import com.usic.uniFex.model.service.NoticiasEventPublisher;
import com.usic.uniFex.model.service.NoticiasService;
import com.usic.uniFex.model.service.NoticiasService.Datos;
import com.usic.uniFex.model.service.NoticiasService.Resultado;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * CRUD de noticias para el panel de admin (V42). Mismo permiso que "Noches de FEXPO"
 * ({@link Roles#ADMINISTRA}): es contenido de la misma vista publica.
 *
 * Alta y edicion van en multipart (datos + archivo en una sola peticion): la foto o el video es
 * obligatorio, y subirlo aparte podria dejar una noticia guardada sin medio. La edicion usa POST
 * y no PATCH porque no todos los clientes mandan multipart con PATCH.
 *
 * La vista publica NO usa este controlador: lee de {@code GET /api/publico/feria} y recibe los
 * cambios por WebSocket en {@code /topic/publico/noticias} (ver {@link NoticiasEventPublisher}).
 */
@RestController
@RequestMapping("/api/app/noticias")
@RequiredArgsConstructor
@PreAuthorize(Roles.ADMINISTRA)
public class NoticiasApiController {

    private final NoticiasService servicio;
    private final NoticiasEventPublisher publisher;

    @GetMapping
    public List<NoticiaDTO> listar() {
        return servicio.listarDeEdicionActiva().stream().map(NoticiaDTO::de).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> crear(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer dia,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String texto,
            @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        return responder(servicio.crear(new Datos(fecha, dia, titulo, texto), archivo, actorId()));
    }

    /** {@code archivo} opcional: si no llega, la noticia conserva su foto o video. */
    @PostMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> editar(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer dia,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String texto,
            @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        return responder(servicio.editar(id, new Datos(fecha, dia, titulo, texto), archivo, actorId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        return responder(servicio.eliminar(id, actorId()));
    }

    private ResponseEntity<Map<String, Object>> responder(Resultado r) {
        // Fuera de la transaccion del servicio: lo que se difunde ya quedo guardado.
        if (r.ok()) publisher.publicar();
        // LinkedHashMap y no Map.of: `noticia` es null cuando falla, y Map.of no admite null.
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("noticia", r.noticia() != null ? NoticiaDTO.de(r.noticia()) : null);
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }

    private Long actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }
}
