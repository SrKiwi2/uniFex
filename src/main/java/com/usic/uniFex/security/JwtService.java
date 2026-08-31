package com.usic.uniFex.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Genera y valida los JWT del API movil/SPA (Fase 2). Firma HMAC-SHA con el secreto configurado. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${unifex.jwt.secret}") String secret,
                      @Value("${unifex.jwt.expiration-ms:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Emite un token para el usuario con su id, username (subject) y rol. */
    public String generar(Usuario u) {
        Date ahora = new Date();
        String rol = (u.getRol() != null) ? u.getRol().getNombre() : null;
        return Jwts.builder()
                .subject(u.getUsername())
                .claim("uid", u.getId())
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Valida la firma y expiracion; devuelve el principal. Lanza excepcion si el token no es valido. */
    public JwtUser validar(String token) {
        Claims c = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        Long uid = (c.get("uid") instanceof Number n) ? n.longValue() : null;
        return new JwtUser(uid, c.getSubject(), c.get("rol", String.class));
    }
}
