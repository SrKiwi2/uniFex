package com.usic.uniFex;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.uniFex.Config.CodificadorErrores;
import com.usic.uniFex.Config.RegistroErroresFilter;
import com.usic.uniFex.model.service.RegistroErroresService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

class RegistroErroresTest {
    @TempDir Path directorio;
    final ObjectMapper json = new ObjectMapper();

    @Test void descargaCompletaYVaciaSoloElArchivoSeleccionado() throws Exception {
        var servicio = new RegistroErroresService(directorio.toString(), json);
        String texto = "Error de José\nSegunda línea\n";
        Files.writeString(directorio.resolve("errores.txt"), texto);
        Files.writeString(directorio.resolve("errores.2026-09-15.0.txt"), "histórico");
        assertEquals(texto, new String(servicio.descargar("errores.txt"), StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> servicio.vaciar("../otro.txt"));
        assertThrows(IllegalArgumentException.class, () -> servicio.descargar("../otro.txt"));
        servicio.vaciar("errores.txt");
        assertEquals(0, Files.size(directorio.resolve("errores.txt")));
        assertEquals("histórico", Files.readString(directorio.resolve("errores.2026-09-15.0.txt")));
    }

    @Test void conservaUsuarioEnTrabajoAsincronoInclusoSiFalla() {
        MDC.put("usuario", "vendedor");
        Runnable tarea = com.usic.uniFex.Config.ContextoRegistro.conservar(() -> {
            assertEquals("vendedor", MDC.get("usuario"));
            throw new IllegalStateException("fallo de envio");
        });
        MDC.put("usuario", "hilo-anterior");
        try {
            assertThrows(IllegalStateException.class, tarea::run);
            assertEquals("hilo-anterior", MDC.get("usuario"));
        } finally { MDC.clear(); }
    }

    @Test void paginaSinPerderNiRepetirErroresConAcentos() throws Exception {
        var texto = new StringBuilder();
        for (int i = 0; i < 230; i++) texto.append(json.writeValueAsString(
                Map.of("usuario", "José", "mensaje", "Falló " + i))).append('\n');
        Files.writeString(directorio.resolve("errores.txt"), texto);
        var servicio = new RegistroErroresService(directorio.toString(), json);
        var vistos = new HashSet<String>();
        Long cursor = null;
        do {
            var pagina = servicio.leer("errores.txt", cursor, "falló", "josé");
            assertTrue(pagina.errores().size() <= 100);
            for (var error : pagina.errores()) assertTrue(vistos.add((String) error.get("mensaje")));
            cursor = pagina.anterior();
        } while (cursor > 0);
        assertEquals(230, vistos.size());
        assertEquals("Falló 229", servicio.leer("errores.txt", null, "", "").errores().getFirst().get("mensaje"));
    }

    @Test void limitaLecturaYRecuperaLineasEnElBordeDelTramo() throws Exception {
        String mensaje = "ñ".repeat(3000);
        var texto = new StringBuilder();
        for (int i = 0; i < 500; i++) texto.append(json.writeValueAsString(
                Map.of("usuario", "Ana", "mensaje", mensaje, "numero", i))).append('\n');
        Files.writeString(directorio.resolve("errores.txt"), texto);
        var servicio = new RegistroErroresService(directorio.toString(), json);
        var vistos = new HashSet<Object>();
        Long cursor = null;
        do {
            var pagina = servicio.leer("errores.txt", cursor, "", "");
            for (var error : pagina.errores()) assertTrue(vistos.add(error.get("numero")));
            cursor = pagina.anterior();
        } while (cursor > 0);
        assertEquals(500, vistos.size());
        assertTrue(servicio.leer("errores.txt", null, "inexistente", "").anterior() > 0);
    }

    @Test void noPermiteLeerOtrosArchivosNiFallaSiAunNoHayErrores() throws Exception {
        var servicio = new RegistroErroresService(directorio.toString(), json);
        assertTrue(servicio.leer("errores.txt", null, "", "").errores().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> servicio.leer("../application.properties", null, "", ""));
        assertThrows(IllegalArgumentException.class, () -> servicio.leer("errores.txt", -1L, "", ""));
        Files.writeString(directorio.resolve("secreto.txt"), "no listar");
        Files.writeString(directorio.resolve("errores.2026-09-15.0.txt"), "");
        assertEquals(java.util.List.of("errores.2026-09-15.0.txt"), servicio.archivos());
    }

    @Test void codificaUnaLineaConIdentidadTrazaYSecretosOcultos() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("prueba.errores");
        var evento = new LoggingEvent("prueba", logger, Level.ERROR,
                "Falló\npassword=privado Authorization: Bearer abc.def.ghi", new IllegalStateException("token=secreto"), null);
        evento.setMDCPropertyMap(Map.of("usuario", "José", "usuarioId", "7"));
        String linea = new String(new CodificadorErrores().encode(evento), StandardCharsets.UTF_8);
        assertEquals(1, linea.lines().count());
        assertFalse(linea.contains("privado"));
        assertFalse(linea.contains("abc.def.ghi"));
        assertFalse(linea.contains("token=secreto"));
        var registro = json.readTree(linea);
        assertEquals("José", registro.get("usuario").asText());
        assertTrue(registro.get("detalle").asText().contains("IllegalStateException"));
    }

    @Test void conservaIdentidadAlResponderYLaLimpiaEntrePeticiones() throws Exception {
        var usuarios = new java.util.ArrayList<String>();
        Logger logger = (Logger) LoggerFactory.getLogger(RegistroErroresFilter.class);
        var appender = new AppenderBase<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent evento) { usuarios.add(evento.getMDCPropertyMap().get("usuario")); }
        };
        appender.start();
        logger.addAppender(appender);
        MDC.clear();
        try {
            var filtro = new RegistroErroresFilter();
            var respuesta = new MockHttpServletResponse();
            filtro.doFilter(new MockHttpServletRequest("GET", "/api/app/prueba"), respuesta, (req, res) -> {
                MDC.put("usuario", "vendedor1");
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(409);
            });
            assertNull(MDC.get("usuario"));
            filtro.doFilter(new MockHttpServletRequest("GET", "/ws"), new MockHttpServletResponse(), (req, res) -> {
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(400);
            });
            assertEquals(java.util.List.of("vendedor1", "Sin autenticar"), usuarios);
            assertNull(MDC.get("usuario"));
        } finally { logger.detachAppender(appender); MDC.clear(); }
    }
}
