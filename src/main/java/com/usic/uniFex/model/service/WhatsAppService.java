package com.usic.uniFex.model.service;

import java.io.IOException;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Envía mensajes de WhatsApp usando la API externa (WaAPI / similar).
 *
 * <p>Soporta texto y documentos (PDF). Se usa tras registrar una venta para enviar al
 * cliente (celular del responsable legal) un mensaje de bienvenida con su credencial
 * virtual y recibo de compra, ambos en PDF adjunto.</p>
 *
 * <p>Configuracion en {@code application.properties}:
 * <ul>
 *   <li>{@code whatsapp.api.url} - endpoint base (sin /sendText), p.ej. {@code http://172.16.21.2:9191/message/}</li>
 *   <li>{@code whatsapp.api.key} - apikey del header</li>
 *   <li>{@code whatsapp.enabled} - {@code true/false}</li>
 *   <li>{@code whatsapp.instance} - nombre de la instancia (p.ej. {@code FEXPO%20UAP%20V2})</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${whatsapp.api.url:}")
    private String apiBaseUrl;

    @Value("${whatsapp.api.key:}")
    private String apiKey;

    @Value("${whatsapp.instance:}")
    private String instance;

    @Value("${whatsapp.enabled:false}")
    private boolean enabled;

    public boolean habilitado() {
        return enabled && apiBaseUrl != null && !apiBaseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && instance != null && !instance.isBlank();
    }

    /**
     * Envía un mensaje de texto simple.
     */
    public boolean enviarTexto(String numero, String mensaje) {
        if (!habilitado()) {
            log.debug("WhatsApp deshabilitado o sin configuración; no se envía a {}", numero);
            return false;
        }
        if (numero == null || numero.isBlank()) {
            log.warn("Número de destino vacío; no se envía WhatsApp");
            return false;
        }

        try {
            var payload = new Payload(numero, mensaje);
            String json = mapper.writeValueAsString(payload);

            RequestBody body = RequestBody.create(json, JSON);
            String url = apiBaseUrl + "sendText/" + instance;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    log.info("WhatsApp texto enviado a {}: {}", numero, respBody);
                    return true;
                } else {
                    log.warn("WhatsApp texto falló ({}) a {}: {}", response.code(), numero, respBody);
                    return false;
                }
            }
        } catch (IOException e) {
            log.error("Error enviando WhatsApp texto a {}: {}", numero, e.getMessage());
            return false;
        }
    }

    /**
     * Envía un documento PDF por WhatsApp.
     *
     * @param numero   destino (formato 591xxxxxxx)
     * @param pdfBytes contenido del PDF
     * @param fileName nombre del archivo (p.ej. "recibo-123.pdf")
     * @param caption  texto acompañante (opcional)
     * @return true si 2xx
     */
    public boolean enviarDocumento(String numero, byte[] pdfBytes, String fileName, String caption) {
        return enviarMedia(numero, pdfBytes, fileName, caption, "document", "application/pdf");
    }

    public boolean enviarImagen(String numero, byte[] imagenBytes, String fileName, String caption) {
        return enviarMedia(numero, imagenBytes, fileName, caption, "image", "image/png");
    }

    private boolean enviarMedia(String numero, byte[] bytes, String fileName, String caption,
                                String mediatype, String mimetype) {
        if (!habilitado()) {
            log.debug("WhatsApp deshabilitado o sin configuración; no se envía archivo a {}", numero);
            return false;
        }
        if (numero == null || numero.isBlank() || bytes == null || bytes.length == 0) {
            log.warn("Parámetros inválidos para enviar archivo a {}", numero);
            return false;
        }

        try {
            var payload = new DocumentoPayload(numero, mediatype, mimetype,
                    caption != null ? caption : "", Base64.getEncoder().encodeToString(bytes), fileName);
            RequestBody requestBody = RequestBody.create(mapper.writeValueAsString(payload), JSON);

            String url = apiBaseUrl + "sendMedia/" + instance;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    log.info("WhatsApp archivo enviado a {} ({})", numero, fileName);
                    return true;
                } else {
                    log.warn("WhatsApp archivo falló ({}) a {}: {}", response.code(), numero, respBody);
                    return false;
                }
            }
        } catch (IOException e) {
            log.error("Error enviando WhatsApp archivo a {}: {}", numero, e.getMessage());
            return false;
        }
    }

    /**
     * Envía el mensaje de bienvenida post-venta con PDFs adjuntos:
     * - 1 recibo de compra
     * - N credenciales virtuales (una por responsable)
     *
     * Requiere inyectar {@link ReciboPdfService} y {@link CredencialPdfService} desde el llamador,
     * o generar los PDFs aparte y pasarlos como bytes.
     */
    public void enviarBienvenidaVentaConPdfs(String celular, String nombreEntidad, Long inscripcionId,
                                             byte[] reciboPdf, java.util.List<byte[]> credencialesPng,
                                             String baseUrl) {
        if (!habilitado()) {
            log.debug("WhatsApp deshabilitado; solo se loguea la intención de envío a {}", celular);
            return;
        }

        // 1. Texto de bienvenida. Los documentos van adjuntos, no como enlaces.
        String mensaje = String.join("\n",
                "🎉 ¡Bienvenido a la FEXPO UAP!",
                "",
                "Hola " + (nombreEntidad != null ? nombreEntidad : "expositor") + ",",
                "Tu inscripción ha sido registrada con éxito.",
                "",
                "📎 Adjunto encontrarás tu recibo en PDF y tus credenciales virtuales.",
                "Registra este número para recibir cualquier novedad de la feria.",
                "",
                "Mensaje enviado desde el sistema automatizado de la Universidad Amazónica de Pando.",
                "¡Nos vemos en la feria!");

        enviarTexto(celular, mensaje);

        // 2. Recibo PDF
        if (reciboPdf != null && reciboPdf.length > 0) {
            enviarDocumento(celular, reciboPdf, "recibo-" + inscripcionId + ".pdf",
                    "📄 Recibo de compra - Inscripción #" + inscripcionId);
        }

        // 3. Credenciales virtuales (una por responsable), iguales a las descargables.
        if (credencialesPng != null) {
            for (int i = 0; i < credencialesPng.size(); i++) {
                byte[] credencial = credencialesPng.get(i);
                if (credencial != null && credencial.length > 0) {
                    enviarImagen(celular, credencial,
                            "credencial-" + inscripcionId + "-" + (i + 1) + ".png",
                            "🎫 Credencial #" + (i + 1) + " - Inscripción #" + inscripcionId);
                }
            }
        }
    }

    private record Payload(String number, String text) {
    }

    private record DocumentoPayload(String number, String mediatype, String mimetype,
                                    String caption, String media, String fileName) {
    }
}
