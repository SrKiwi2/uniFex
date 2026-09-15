package com.usic.uniFex.controller.usuario;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.PresenciaService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Seguimiento en vivo: quien esta dentro y que esta haciendo.
 *
 * El latido lo manda CUALQUIER usuario autenticado (es el que dice "sigo aqui"); la lista solo la
 * ve administracion, porque es informacion sobre el trabajo de los demas.
 *
 * Lo que el cliente informa —en que pantalla esta— se toma tal cual: es para mirar, no autoriza
 * nada. Lo que NO se toma bajo palabra es si esta vendiendo: eso sale de la base, de las casetas
 * que tiene reservadas de verdad. Un cliente puede decir cualquier cosa; las reservas no mienten.
 */
@RestController
@RequestMapping("/api/app/presencia")
@RequiredArgsConstructor
public class PresenciaApiController {

    private final PresenciaService presencia;
    private final IUsuarioDao usuarioDao;
    private final IPuestoDao puestoDao;

    /** Lo que el cliente informa de si mismo en cada latido. */
    public record Latido(String pantalla, String titulo, String origen) {
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> latir(@RequestBody(required = false) Latido req) {
        JwtUser u = actual();
        if (u == null) return ResponseEntity.status(401).body(Map.of("ok", false));
        Usuario fila = usuarioDao.findById(u.id()).orElse(null);
        presencia.latir(u.id(), u.username(), nombreDe(fila), u.rol(),
                req == null ? null : req.pantalla(),
                req == null ? null : req.titulo(),
                req == null ? null : req.origen());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Cierre de sesion: se va por la puerta, no hay que esperar a que venza su silencio. */
    @PostMapping("/salir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> salir() {
        JwtUser u = actual();
        if (u != null) presencia.olvidar(u.id());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Quien esta dentro, que pantalla tiene abierta y si esta a media venta.
     *
     * `casetasEnCarrito` sale de las casetas en tramite a su nombre: son ventas EMPEZADAS y sin
     * cerrar. Es el dato que de verdad se busca al abrir esta pantalla —quien tiene algo a medias
     * y lleva rato sin moverlo— y por eso no se deduce de la pantalla que dice tener abierta.
     */
    @GetMapping
    @PreAuthorize(Roles.ADMINISTRA)
    public List<Map<String, Object>> listar() {
        Instant ahora = Instant.now();
        return presencia.listar().stream().map(p -> {
            List<Puesto> carrito = puestoDao.reservadasPor(p.usuarioId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("usuarioId", p.usuarioId());
            m.put("usuario", p.usuario());
            m.put("nombre", p.nombre());
            m.put("rol", p.rol());
            m.put("pantalla", p.pantalla());
            m.put("titulo", p.titulo());
            m.put("origen", p.origen());
            m.put("enLinea", PresenciaService.estaVivo(p));
            m.put("inactivoSegundos", Math.max(0, ahora.getEpochSecond() - p.visto().getEpochSecond()));
            m.put("casetasEnCarrito", carrito.size());
            m.put("casetas", carrito.stream().map(Puesto::getCodigo).toList());
            // "Registrando" es tener casetas tomadas, este o no en esa pantalla: si dejo el
            // formulario a medias y se fue al mapa, la venta sigue abierta y las casetas, bloqueadas.
            m.put("registrando", !carrito.isEmpty());
            return m;
        }).toList();
    }

    private static String nombreDe(Usuario u) {
        if (u == null) return null;
        Persona p = u.getPersona();
        if (p == null) return u.getUsername();
        String completo = String.join(" ",
                java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                        .filter(x -> x != null && !x.isBlank()).toList());
        return completo.isBlank() ? u.getUsername() : completo;
    }

    private JwtUser actual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju : null;
    }
}
