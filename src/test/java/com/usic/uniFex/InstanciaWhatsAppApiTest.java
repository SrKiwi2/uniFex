package com.usic.uniFex;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.usic.uniFex.Config.*;
import com.usic.uniFex.controller.api.InstanciaWhatsAppApiController;
import com.usic.uniFex.model.service.InstanciaWhatsAppService;
import com.usic.uniFex.model.service.MantenimientoService;
import com.usic.uniFex.security.JwtService;
import com.usic.uniFex.security.JwtUser;

@WebMvcTest
@ActiveProfiles("dev")
@ContextConfiguration(classes = {InstanciaWhatsAppApiController.class, SecurityConfig.class, ManejadorErroresApi.class})
class InstanciaWhatsAppApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean MantenimientoService mantenimiento;
    @MockitoBean InstanciaWhatsAppService servicio;
    private static final String RUTA = "/api/app/whatsapp/instancias";

    @BeforeEach void preparar() {
        when(jwt.validar("admin")).thenReturn(new JwtUser(1L, "admin", "ADMINISTRADOR"));
        when(jwt.validar("vendedor")).thenReturn(new JwtUser(2L, "vendedor", "ADMINISTRATIVO"));
        when(mantenimiento.estado()).thenReturn(mock(com.usic.uniFex.model.dto.MantenimientoDTO.class));
    }

    @Test void lecturaYEscriturasSoloAdministrativas() throws Exception {
        for (var solicitud : java.util.List.of(get(RUTA), post(RUTA).content("{}"),
                put(RUTA + "/1").content("{}"), patch(RUTA + "/1/estado").content("{\"activa\":true}"))) {
            mvc.perform(solicitud.contentType("application/json")).andExpect(status().isUnauthorized());
            mvc.perform(solicitud.header("Authorization", "Bearer vendedor")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
        mvc.perform(get(RUTA).header("Authorization", "Bearer admin")).andExpect(status().isOk());
    }

    @Test void conflictosDevuelvenJsonSinRedireccion() throws Exception {
        when(servicio.cambiarEstado(1L, false)).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Activa otra instancia."));
        mvc.perform(patch(RUTA + "/1/estado").header("Authorization", "Bearer admin")
                .contentType("application/json").content("{\"activa\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje").value("Activa otra instancia."));
    }
}
