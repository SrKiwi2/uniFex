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
import com.usic.uniFex.security.JwtService;

import lombok.RequiredArgsConstructor;

/** Autenticacion stateless del API movil/SPA: devuelve un JWT (Fase 2). */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IUsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public record LoginRequest(String usuario, String contrasena) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        Usuario u = usuarioService.findByUsername(req.usuario()).orElse(null);
        if (u == null || !passwordEncoder.matches(req.contrasena(), u.getPassword())) {
            return ResponseEntity.status(401)
                    .body(Map.<String, Object>of("ok", false, "mensaje", "Usuario o contrasena incorrectos"));
        }
        if ("INACTIVO".equals(u.getEstado())) {
            return ResponseEntity.status(403)
                    .body(Map.<String, Object>of("ok", false, "mensaje", "Usuario inactivo"));
        }
        String rol = (u.getRol() != null && u.getRol().getNombre() != null) ? u.getRol().getNombre() : "";
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
