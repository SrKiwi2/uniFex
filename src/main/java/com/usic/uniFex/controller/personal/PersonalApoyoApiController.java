package com.usic.uniFex.controller.personal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dto.PersonalApoyoDTO;
import com.usic.uniFex.model.entity.Dependencia;
import com.usic.uniFex.model.service.GestionPersonalApoyoService;
import com.usic.uniFex.model.service.GestionPersonalApoyoService.Datos;
import com.usic.uniFex.model.service.GestionPersonalApoyoService.DependenciaDatos;
import com.usic.uniFex.model.service.GestionPersonalApoyoService.DependenciaResultado;
import com.usic.uniFex.model.service.GestionPersonalApoyoService.Resultado;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * API para gestión de personal de apoyo de la feria (fotógrafos, azafatas, logística, etc.).
 * Módulo aislado: no toca la tabla persona ni usuario existentes.
 */
@RestController
@RequestMapping("/api/app/personal-apoyo")
@RequiredArgsConstructor
@PreAuthorize(Roles.USA_PERSONAL_APOYO)
public class PersonalApoyoApiController {

    private final GestionPersonalApoyoService gestion;

    // ===== DEPENDENCIAS (solo SUPER USUARIO) =====

    @GetMapping("/dependencias")
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public List<Map<String, Object>> listarDependencias() {
        return gestion.listarDependencias().stream()
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", d.getId());
                    m.put("nombre", d.getNombre());
                    m.put("descripcion", d.getDescripcion());
                    return m;
                })
                .toList();
    }

    @PostMapping("/dependencias")
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public ResponseEntity<Map<String, Object>> crearDependencia(@RequestBody DependenciaDatos req) {
        return responderDependencia(gestion.crearDependencia(req, actorId()));
    }

    @PatchMapping("/dependencias/{id}")
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public ResponseEntity<Map<String, Object>> editarDependencia(@PathVariable Long id, @RequestBody DependenciaDatos req) {
        return responderDependencia(gestion.editarDependencia(id, req, actorId()));
    }

    @DeleteMapping("/dependencias/{id}")
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public ResponseEntity<Map<String, Object>> eliminarDependencia(@PathVariable Long id) {
        return responderDependencia(gestion.eliminarDependencia(id, actorId()));
    }

    // ===== PERSONAL (recortado a la dependencia propia salvo super usuario) =====

    /**
     * Lo que la vista necesita para pintarse: si eres super usuario ves todo; si no, tu
     * dependencia (o nada, si tu usuario no tiene ficha de apoyo con tu CI).
     */
    @GetMapping("/mi-dependencia")
    public Map<String, Object> miDependencia() {
        Map<String, Object> m = new HashMap<>();
        boolean superUsuario = esSuperUsuario();
        m.put("esSuper", superUsuario);
        if (!superUsuario) {
            gestion.miDependencia(actorId()).ifPresent(d -> {
                m.put("idDependencia", d.getId());
                m.put("dependenciaNombre", d.getNombre());
            });
        }
        return m;
    }

    @GetMapping
    public List<PersonalApoyoDTO> listar(@RequestParam(required = false) Long dependencia) {
        if (esSuperUsuario()) {
            if (dependencia != null) {
                return gestion.listarPorDependencia(dependencia).stream()
                        .map(PersonalApoyoDTO::de)
                        .toList();
            }
            return gestion.listarPersonal().stream()
                    .map(PersonalApoyoDTO::de)
                    .toList();
        }
        // Sin super: solo la propia, aunque el cliente pida otra en el parametro.
        return gestion.miDependencia(actorId())
                .map(d -> gestion.listarPorDependencia(d.getId()).stream()
                        .map(PersonalApoyoDTO::de)
                        .toList())
                .orElse(List.of());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Datos req) {
        return responder(gestion.crearPersonal(req, actorId(), alcance()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody Datos req) {
        return responder(gestion.editarPersonal(id, req, actorId(), alcance()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        return responder(gestion.eliminarPersonal(id, actorId(), alcance()));
    }

    @PostMapping(value = "/{id}/foto",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirFoto(
            @PathVariable Long id, @RequestParam("foto") MultipartFile foto) {
        return responder(gestion.guardarFoto(id, foto, actorId(), alcance()));
    }

    @DeleteMapping("/{id}/foto")
    public ResponseEntity<Map<String, Object>> quitarFoto(@PathVariable Long id) {
        return responder(gestion.quitarFoto(id, actorId(), alcance()));
    }

    // ===== helpers =====

    private ResponseEntity<Map<String, Object>> responder(Resultado r) {
        boolean tiene = r.personal() != null;
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        if (tiene) cuerpo.put("personal", PersonalApoyoDTO.de(r.personal()));
        return ResponseEntity.status(r.ok() ? 200 : 400).body(cuerpo);
    }

    private ResponseEntity<Map<String, Object>> responderDependencia(DependenciaResultado r) {
        boolean tiene = r.dependencia() != null;
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        if (tiene) {
            Map<String, Object> dep = new HashMap<>();
            dep.put("id", r.dependencia().getId());
            dep.put("nombre", r.dependencia().getNombre());
            dep.put("descripcion", r.dependencia().getDescripcion());
            cuerpo.put("dependencia", dep);
        }
        return ResponseEntity.status(r.ok() ? 200 : 400).body(cuerpo);
    }

    private Long actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }

    /** true si quien llama es SUPER USUARIO (sin recorte de dependencia). */
    private boolean esSuperUsuario() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof JwtUser ju
                && "SUPER_USUARIO".equals(ju.rolNormalizado());
    }

    /**
     * El recorte para este actor: null = ve y toca todo; si no, solo su dependencia (o nada,
     * si no tiene ficha: el servicio rechaza la escritura y el listado sale vacio).
     */
    private Long alcance() {
        if (esSuperUsuario()) return null;
        return gestion.miDependencia(actorId()).map(Dependencia::getId).orElse(-1L);
    }
}