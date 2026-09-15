package com.usic.uniFex.model.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;

import com.usic.uniFex.model.dao.IConfiguracionSistemaDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.dto.MantenimientoDTO;
import com.usic.uniFex.model.entity.ConfiguracionSistema;
import com.usic.uniFex.security.RolesSistema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MantenimientoService {

    public static final String TIPO_MANTENIMIENTO = "MANTENIMIENTO_ACTIVO";
    public static final String MENSAJE_PREDETERMINADO = "Sistema no disponible de 9:30 pm a 6 am por verificacion y conciliacion.";

    private final IConfiguracionSistemaDao configuracionDao;
    private final IUsuarioDao usuarioDao;
    private final NotificacionService notificaciones;

    @Transactional(readOnly = true)
    public MantenimientoDTO estado() {
        ConfiguracionSistema c;
        try {
            c = configuracionDao.findById(ConfiguracionSistema.CLAVE_MANTENIMIENTO).orElse(null);
        } catch (DataAccessException e) {
            log.warn("No se pudo leer configuracion_sistema; falta aplicar V29__modo_mantenimiento.sql: {}",
                    e.getMessage());
            return new MantenimientoDTO(false, MENSAJE_PREDETERMINADO);
        }
        if (c == null) return new MantenimientoDTO(false, MENSAJE_PREDETERMINADO);
        return new MantenimientoDTO(Boolean.TRUE.equals(c.getActivo()), mensaje(c.getMensaje()));
    }

    @Transactional
    public MantenimientoDTO guardar(boolean activo, String mensaje, Long usuarioId) {
        ConfiguracionSistema c = configuracionDao.findById(ConfiguracionSistema.CLAVE_MANTENIMIENTO)
                .orElseGet(() -> {
                    ConfiguracionSistema nuevo = new ConfiguracionSistema();
                    nuevo.setClave(ConfiguracionSistema.CLAVE_MANTENIMIENTO);
                    return nuevo;
                });
        boolean estabaActivo = Boolean.TRUE.equals(c.getActivo());
        c.setActivo(activo);
        c.setMensaje(mensaje(mensaje));
        c.setActualizadoEn(LocalDateTime.now());
        c.setActualizadoPor(usuarioId);
        configuracionDao.save(c);

        if (activo && !estabaActivo) avisarExpulsion(c.getMensaje());
        log.info("Modo mantenimiento {} por usuario {}", activo ? "activado" : "desactivado", usuarioId);
        return new MantenimientoDTO(Boolean.TRUE.equals(c.getActivo()), c.getMensaje());
    }

    public boolean permiteRol(String rol) {
        return RolesSistema.SUPER_USUARIO.getNombre().equals(RolesSistema.normalizar(rol));
    }

    private void avisarExpulsion(String mensaje) {
        for (Long usuarioId : usuarioDao.idsNoSuperUsuario()) {
            notificaciones.notificar(usuarioId, TIPO_MANTENIMIENTO, "Sistema en mantenimiento", mensaje, null, null);
        }
    }

    private String mensaje(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        return limpio.isEmpty() ? MENSAJE_PREDETERMINADO : limpio;
    }
}
