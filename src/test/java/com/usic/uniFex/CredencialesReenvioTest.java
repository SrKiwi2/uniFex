package com.usic.uniFex;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.usic.uniFex.controller.credenciales.CredencialesApiController;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.service.*;

@ExtendWith(MockitoExtension.class)
class CredencialesReenvioTest {
    @Mock CredencialService credencialService;
    @Mock CredencialImagenService imagenService;
    @Mock ReciboPdfService reciboPdfService;
    @Mock WhatsAppService whatsApp;
    @Mock IInscripcionService inscripcionService;
    @InjectMocks CredencialesApiController controlador;
    MockMvc mvc;
    CredencialDTO credencial;

    @BeforeEach void preparar() {
        ReflectionTestUtils.setField(controlador, "baseUrlPublica", "https://feria.test");
        mvc = MockMvcBuilders.standaloneSetup(controlador).build();
        var entidad = new Entidad();
        entidad.setNombre("Entidad de prueba");
        entidad.setCelularRepresentante("70000000");
        var inscripcion = new Inscripcion();
        inscripcion.setEntidad(entidad);
        when(inscripcionService.findById(1L)).thenReturn(inscripcion);
        when(whatsApp.habilitado()).thenReturn(true);
        credencial = mock(CredencialDTO.class);
        when(credencial.responsableId()).thenReturn(2L);
        when(credencial.apto(PlantillaCredencial.CREDENCIAL_VIRTUAL.id())).thenReturn(true);
        when(credencialService.porInscripcion(1L)).thenReturn(List.of(credencial));
        when(imagenService.generar(eq(credencial), eq(PlantillaCredencial.CREDENCIAL_VIRTUAL), anyString()))
                .thenReturn(new byte[]{10});
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void extraIncluyeSuReciboAunqueNoSePidaElDeVenta(boolean incluirRecibo) throws Exception {
        when(credencial.esExtra()).thenReturn(true);
        when(credencial.nombre()).thenReturn("Responsable extra");
        var noSeleccionada = mock(CredencialDTO.class);
        when(noSeleccionada.responsableId()).thenReturn(3L);
        when(credencialService.porInscripcion(1L)).thenReturn(List.of(credencial, noSeleccionada));
        doAnswer(invocacion -> {
            invocacion.<ByteArrayOutputStream>getArgument(1).write(new byte[]{30});
            return null;
        }).when(reciboPdfService).generarReciboResponsableExtra(eq(2L), any(ByteArrayOutputStream.class));
        mvc.perform(post("/api/app/credenciales/whatsapp").contentType("application/json")
                .content("{\"inscripcionId\":1,\"responsables\":[2],\"incluirRecibo\":" + incluirRecibo + "}"))
                .andExpect(status().isOk());
        verify(reciboPdfService).generarReciboResponsableExtra(eq(2L), any(ByteArrayOutputStream.class));
        verify(reciboPdfService, never()).generarReciboResponsableExtra(eq(3L), any());
        verify(reciboPdfService, times(incluirRecibo ? 1 : 0)).generarRecibo(eq(1L), any());
        verify(whatsApp).enviarBienvenidaVentaConPdfs(anyString(), anyString(), eq(1L),
                nullable(byte[].class), anyList(), anyString(), argThat(recibos -> recibos.size() == 1
                        && recibos.getFirst().responsableId().equals(2L)
                        && java.util.Arrays.equals(recibos.getFirst().pdf(), new byte[]{30})));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false, true})
    void reciboSoloCuandoSeSolicitaExplicitamente(Boolean incluirRecibo) throws Exception {
        if (Boolean.TRUE.equals(incluirRecibo)) {
            doAnswer(invocacion -> {
                invocacion.<ByteArrayOutputStream>getArgument(1).write(new byte[]{20});
                return null;
            }).when(reciboPdfService).generarRecibo(eq(1L), any(ByteArrayOutputStream.class));
        }
        String opcion = incluirRecibo == null ? "" : ",\"incluirRecibo\":" + incluirRecibo;
        mvc.perform(post("/api/app/credenciales/whatsapp").contentType("application/json")
                .content("{\"inscripcionId\":1,\"responsables\":[2]" + opcion + "}"))
                .andExpect(status().isOk());
        if (Boolean.TRUE.equals(incluirRecibo)) {
            verify(reciboPdfService).generarRecibo(eq(1L), any(ByteArrayOutputStream.class));
            verify(whatsApp).enviarBienvenidaVentaConPdfs(eq("59170000000"), anyString(), eq(1L),
                    aryEq(new byte[]{20}), anyList(), anyString(), eq(List.of()));
        } else {
            verifyNoInteractions(reciboPdfService);
            verify(whatsApp).enviarBienvenidaVentaConPdfs(eq("59170000000"), anyString(), eq(1L),
                    isNull(), anyList(), anyString(), eq(List.of()));
        }
    }
}
