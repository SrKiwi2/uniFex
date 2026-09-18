package com.usic.uniFex.controller.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.GestionPersonalApoyoService;
import com.usic.uniFex.model.service.MantenimientoService;
import com.usic.uniFex.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Autenticacion stateless del API movil/SPA: devuelve un JWT (Fase 2). */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final IUsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MantenimientoService mantenimientoService;
    private final GestionPersonalApoyoService personalApoyo;

    public record LoginRequest(String usuario, String contrasena) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        Usuario u = usuarioService.findByUsername(req.usuario()).orElse(null);
        // Contrasena nula = login fallido (401), no un 500: BCrypt lanza si recibe null.
        if (u == null || req.contrasena() == null || !passwordEncoder.matches(req.contrasena(), u.getPassword())) {
            return ResponseEntity.status(401)
                    .body(Map.<String, Object>of("ok", false, "mensaje", "Usuario o contrasena incorrectos"));
        }
        // Cualquier estado que no sea ACTIVO cierra la puerta. Antes solo se miraba INACTIVO,
        // asi que un usuario dado de baja logica (_estado = ELIMINADO) seguia pudiendo entrar:
        // desaparecia del modulo de gestion, pero su login seguia vivo. Un _estado nulo se trata
        // como activo, que es lo que hay en las filas antiguas creadas antes de que existiera.
        String estado = u.getEstado();
        if (estado != null && !"ACTIVO".equalsIgnoreCase(estado.trim())) {
            return ResponseEntity.status(403)
                    .body(Map.<String, Object>of("ok", false, "mensaje", "Usuario inactivo"));
        }
        String rol = (u.getRol() != null && u.getRol().getNombre() != null) ? u.getRol().getNombre() : "";
        var mantenimiento = mantenimientoService.estado();
        if (mantenimiento.activo() && !mantenimientoService.permiteRol(rol)) {
            return ResponseEntity.status(423).body(Map.<String, Object>of(
                    "ok", false,
                    "codigo", "MANTENIMIENTO",
                    "mensaje", mantenimiento.mensaje()));
        }
        // Si su persona tiene carrera, esa carrera ya es su dependencia y el ya es su
        // coordinador: el modulo de personal de apoyo lo encuentra por su CI. Va en try/catch
        // a proposito: esto es cortesia, y un fallo aqui nunca puede impedir entrar.
        try {
            personalApoyo.asegurarFichaAlIngresar(u.getId());
        } catch (Exception e) {
            log.warn("No se pudo asegurar la ficha de apoyo al ingresar (usuario {})", u.getId(), e);
        }
        // Se devuelve el id porque el mapa necesita saber cuales de las casetas en tramite
        // son de este vendedor: las compara con el `reservadoPor` que difunde el WebSocket.
        // Va aparte del token para no obligar al cliente a decodificar el JWT.
        return ResponseEntity.ok(Map.<String, Object>of(
                "ok", true,
                "token", jwtService.generar(u),
                "id", u.getId(),
                "usuario", u.getUsername(),
                "rol", rol));
    }
}
