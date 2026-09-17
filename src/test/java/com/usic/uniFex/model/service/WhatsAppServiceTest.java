package com.usic.uniFex.model.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import com.usic.uniFex.model.entity.InstanciaWhatsApp;

/** El proveedor es un servidor local: estas pruebas nunca envian WhatsApp reales. */
class WhatsAppServiceTest {
    HttpServer proveedor;
    InstanciaWhatsAppService instancias;
    WhatsAppService servicio;
    List<String> recibidas;
    List<String> cuerpos;

    @BeforeEach void preparar() throws Exception {
        recibidas = new CopyOnWriteArrayList<>();
        cuerpos = new CopyOnWriteArrayList<>();
        proveedor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proveedor.createContext("/message/", intercambio -> {
            recibidas.add(intercambio.getRequestURI().getRawPath() + "|" + intercambio.getRequestHeaders().getFirst("apikey"));
            cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            intercambio.sendResponseHeaders(200, 2);
            intercambio.getResponseBody().write("{}".getBytes());
            intercambio.close();
        });
        proveedor.start();
        instancias = mock(InstanciaWhatsAppService.class);
        servicio = new WhatsAppService(new MensajesWhatsAppVenta(), instancias);
    }

    @AfterEach void cerrar() { proveedor.stop(0); }

    private InstanciaWhatsApp configuracion(String nombre, String clave) {
        var instancia = new InstanciaWhatsApp();
        instancia.setUrlApi("http://127.0.0.1:" + proveedor.getAddress().getPort() + "/message/");
        instancia.setInstancia(nombre);
        instancia.setClaveApi(clave);
        instancia.setActiva(true);
        return instancia;
    }

    @Test void consultaLaActivaEnCadaNuevoEnvioYAdmiteEspaciosCodificados() {
        when(instancias.activa()).thenReturn(Optional.of(configuracion("FEXPO UAP", "uno")),
                Optional.of(configuracion("Otra%20instancia", "dos")));
        assertThat(servicio.enviarTexto("59170000000", "Prueba")).isTrue();
        assertThat(servicio.enviarTexto("59170000000", "Prueba")).isTrue();
        assertThat(recibidas).containsExactly("/message/sendText/FEXPO%20UAP|uno", "/message/sendText/Otra%20instancia|dos");
    }

    @Test void paqueteCompletoConservaInstanciaYClave() {
        when(instancias.activa()).thenReturn(Optional.of(configuracion("Uno", "uno")), Optional.of(configuracion("Dos", "dos")));
        servicio.enviarBienvenidaVentaConPdfs("59170000000", "Entidad de prueba", 1L,
                new byte[]{1}, List.of(new byte[]{2}), "http://localhost");
        assertThat(recibidas).containsExactly("/message/sendText/Uno|uno", "/message/sendMedia/Uno|uno", "/message/sendMedia/Uno|uno");
        verify(instancias, times(1)).activa();
    }

    @Test void adjuntaReciboExtraConNombrePropioSinReciboGeneral() throws Exception {
        when(instancias.activa()).thenReturn(Optional.of(configuracion("Uno", "uno")));
        servicio.enviarBienvenidaVentaConPdfs("59170000000", "Entidad", 1L, null,
                List.of(new byte[]{2}), "http://localhost",
                List.of(new WhatsAppService.ReciboExtra(7L, "Responsable", new byte[]{30})));
        assertThat(recibidas).hasSize(3);
        var documento = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cuerpos.get(1));
        assertThat(documento.get("fileName").asText()).isEqualTo("recibo-extra-7.pdf");
        assertThat(documento.get("mediatype").asText()).isEqualTo("document");
        assertThat(documento.get("media").asText()).isEqualTo("Hg==");
        verify(instancias, times(1)).activa();
    }

    @Test void sinConfiguracionNoIntentaEnviar() {
        when(instancias.activa()).thenReturn(Optional.empty());
        assertThat(servicio.habilitado()).isFalse();
        assertThat(servicio.enviarTexto("59170000000", "Prueba")).isFalse();
        assertThat(servicio.enviarDocumento("59170000000", new byte[]{1}, "recibo.pdf", "")).isFalse();
        assertThat(recibidas).isEmpty();
    }
}
