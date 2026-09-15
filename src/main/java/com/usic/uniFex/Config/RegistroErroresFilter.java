package com.usic.uniFex.Config;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.usic.uniFex.model.entity.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/** Envuelve tambien la seguridad: conserva la identidad hasta registrar la respuesta final. */
@Component
@Order(-101)
@Slf4j
public class RegistroErroresFilter extends OncePerRequestFilter {
    public static final String REGISTRADO = RegistroErroresFilter.class.getName() + ".registrado";

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        var anterior = MDC.getCopyOfContextMap();
        try {
            MDC.put("peticionId", UUID.randomUUID().toString());
            MDC.put("ruta", peticion.getMethod() + " " + peticion.getRequestURI());
            MDC.put("origen", "APK".equals(peticion.getHeader("X-Origen")) ? "APK" : "WEB");
            MDC.put("usuario", "Sin autenticar");
            MDC.remove("usuarioId");
            var sesion = peticion.getSession(false);
            if (sesion != null && sesion.getAttribute("usuario") instanceof Usuario usuario) {
                MDC.put("usuario", usuario.getUsername());
                MDC.put("usuarioId", String.valueOf(usuario.getId()));
            }
            try {
                cadena.doFilter(peticion, respuesta);
            } catch (IOException | ServletException | RuntimeException e) {
                log.error("Excepcion procesando la peticion", e);
                peticion.setAttribute(REGISTRADO, true);
                throw e;
            } finally {
                if (respuesta.getStatus() >= 400 && peticion.getAttribute(REGISTRADO) == null) {
                    log.error("Peticion rechazada: HTTP {}", respuesta.getStatus());
                }
            }
        } finally {
            if (anterior == null) MDC.clear(); else MDC.setContextMap(anterior);
        }
    }
}
