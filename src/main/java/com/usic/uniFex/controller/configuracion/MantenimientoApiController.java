package com.usic.uniFex.controller.configuracion;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.MantenimientoDTO;
import com.usic.uniFex.model.service.MantenimientoService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/mantenimiento")
@RequiredArgsConstructor
public class MantenimientoApiController {

    private final MantenimientoService mantenimientoService;

    public record MantenimientoRequest(Boolean activo, String mensaje) {
    }

    @GetMapping
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public MantenimientoDTO estado() {
        return mantenimientoService.estado();
    }

    @PutMapping
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public MantenimientoDTO guardar(@RequestBody MantenimientoRequest req) {
        return mantenimientoService.guardar(Boolean.TRUE.equals(req.activo()), req.mensaje(), usuarioActual());
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}
