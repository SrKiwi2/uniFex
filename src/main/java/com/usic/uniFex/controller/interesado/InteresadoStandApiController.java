package com.usic.uniFex.controller.interesado;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.InteresadoStandDTO;
import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.service.CategoriaMapaService;
import com.usic.uniFex.model.service.InteresadoStandEventPublisher;
import com.usic.uniFex.model.service.InteresadoStandService;
import com.usic.uniFex.model.service.InteresadoStandService.Resultado;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Panel "Interesados en exponer": listado y edicion de lo que llega del formulario publico
 * "Quiero exponer" (el alta anonima vive en {@code publico/InteresadoStandController}).
 *
 * Filtros y orden se resuelven en el cliente: son unos cientos de filas como mucho, y asi
 * cada cambio de filtro es instantaneo y no cuesta una peticion.
 */
@RestController
@RequestMapping("/api/app/interesados-stand")
@RequiredArgsConstructor
@PreAuthorize(Roles.ADMINISTRA)
public class InteresadoStandApiController {

    private final InteresadoStandService servicio;
    private final CategoriaMapaService categorias;
    private final InteresadoStandEventPublisher publisher;

    @GetMapping
    public List<InteresadoStandDTO> listar() {
        return servicio.listar().stream().map(InteresadoStandDTO::de).toList();
    }

    /**
     * Categorias vivas, para los filtros y el selector del modal. Aqui y no en
     * /api/app/categorias: aquel exige EDITA_PLANO, y esta pantalla no tiene por que depender
     * de poder rediseñar el plano (hoy son los mismos roles, pero estan separados a proposito).
     */
    @GetMapping("/categorias")
    public List<Map<String, Object>> categorias() {
        return categorias.listar().stream()
                .map(c -> {
                    // LinkedHashMap y no Map.of: color puede ser null y Map.of no lo admite.
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("nombre", c.getNombre());
                    m.put("color", c.getColor());
                    return m;
                })
                .toList();
    }

    /** Sin @Valid a proposito: el servicio valida y devuelve el mensaje exacto del campo. */
    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody InteresadoStandRequest req) {
        Resultado r = servicio.editar(id, req);
        // Los demas paneles abiertos ven la correccion sin recargar.
        if (r.ok()) publisher.publicar(r.interesado());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("interesado", r.interesado() != null ? InteresadoStandDTO.de(r.interesado()) : null);
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }
}
