package com.usic.uniFex.security;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import com.usic.uniFex.model.dto.MantenimientoDTO;
import com.usic.uniFex.model.service.MantenimientoService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MantenimientoApiFilter extends OncePerRequestFilter {

    private final MantenimientoService mantenimientoService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ruta = request.getRequestURI();
        if (!ruta.startsWith(request.getContextPath() + "/api/app/")
                || ruta.startsWith(request.getContextPath() + "/api/app/mantenimiento")) {
            chain.doFilter(request, response);
            return;
        }

        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUser u) {
            MantenimientoDTO estado = mantenimientoService.estado();
            if (estado.activo() && !mantenimientoService.permiteRol(u.rol())) {
                response.setStatus(423);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"ok\":false,\"codigo\":\"MANTENIMIENTO\",\"mensaje\":\""
                        + escapar(estado.mensaje()) + "\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String escapar(String valor) {
        return valor == null ? "" : valor.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
