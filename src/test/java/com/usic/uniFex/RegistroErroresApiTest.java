package com.usic.uniFex;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.usic.uniFex.Config.*;
import com.usic.uniFex.controller.api.RegistroErroresApiController;
import com.usic.uniFex.model.service.MantenimientoService;
import com.usic.uniFex.model.service.RegistroErroresService;
import com.usic.uniFex.security.JwtService;
import com.usic.uniFex.security.JwtUser;

@WebMvcTest(properties = "unifex.logs.directorio=target/logs-prueba")
@org.springframework.test.context.ActiveProfiles("dev")
@ContextConfiguration(classes = {RegistroErroresApiController.class, SecurityConfig.class,
        RegistroErroresFilter.class, ManejadorErroresApi.class, RegistroErroresService.class})
class RegistroErroresApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean MantenimientoService mantenimiento;

    @BeforeEach void preparar() {
        when(jwt.validar("administrador")).thenReturn(new JwtUser(1L, "admin", "ADMINISTRADOR"));
        when(jwt.validar("vendedor")).thenReturn(new JwtUser(2L, "vendedor", "ADMINISTRATIVO"));
        when(mantenimiento.estado()).thenReturn(mock(com.usic.uniFex.model.dto.MantenimientoDTO.class));
    }

    @Test void exigeAutenticacionYRolEnLosDosEndpointsDeLectura() throws Exception {
        for (String ruta : new String[]{"/api/app/errores", "/api/app/errores/archivos"}) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mvc.perform(get(ruta).header("Authorization", "Bearer vendedor")).andExpect(status().isForbidden());
            mvc.perform(get(ruta).header("Authorization", "Bearer administrador")).andExpect(status().isOk());
        }
    }

    @Test void vendedorPuedeInformarPeroNoElegirLaIdentidadDelRegistro() throws Exception {
        mvc.perform(post("/api/app/errores/cliente").header("Authorization", "Bearer vendedor")
                .contentType("application/json")
                .content("{\"mensaje\":\"Error de prueba de interfaz\",\"ruta\":\"/mis-ventas\",\"usuario\":\"admin\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/app/errores").param("buscar", "Error de prueba de interfaz")
                .header("Authorization", "Bearer administrador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errores[0].usuario").value("vendedor"))
                .andExpect(jsonPath("$.errores[0].usuarioId").value("2"));
        org.junit.jupiter.api.Assertions.assertNull(org.slf4j.MDC.get("usuario"));
    }

    @Test void rechazaMensajesExcesivos() throws Exception {
        mvc.perform(post("/api/app/errores/cliente").header("Authorization", "Bearer vendedor")
                .contentType("application/json").content("{\"mensaje\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
