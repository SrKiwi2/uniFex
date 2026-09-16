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
    private final MensajesWhatsAppVenta mensajesVenta;

    @Value("${whatsapp.api.url:}")
    private String apiBaseUrl;

    @Value("${whatsapp.api.key:}")
    private String apiKey;

    @Value("${whatsapp.instance:}")
    private String instance;

    @Value("${whatsapp.enabled:false}")
    private boolean enabled;

    public boolean habilitado() {
        boolean ok = enabled && apiBaseUrl != null && !apiBaseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && instance != null && !instance.isBlank();
        if (!ok) {
            log.info("[WHATSAPP] No habilitado enabled={} apiUrlConfig={} apiKeyConfig={} instanceConfig={}",
                    enabled, apiBaseUrl != null && !apiBaseUrl.isBlank(),
                    apiKey != null && !apiKey.isBlank(), instance != null && !instance.isBlank());
        }
        return ok;
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
            log.info("[WHATSAPP] Enviando archivo numero={} fileName='{}' mediatype={} mimetype={} bytes={} url={}sendMedia/{}",
                    numero, fileName, mediatype, mimetype, bytes.length, apiBaseUrl, instance);
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
                    log.info("[WHATSAPP] Archivo enviado numero={} fileName='{}' status={} respuesta={}",
                            numero, fileName, response.code(), respBody);
                    return true;
                } else {
                    log.error("[WHATSAPP] Archivo fallo numero={} fileName='{}' status={} respuesta={}",
                            numero, fileName, response.code(), respBody);
                    return false;
                }
            }
        } catch (IOException e) {
            log.error("[WHATSAPP] Error enviando archivo numero={} fileName='{}': {}",
                    numero, fileName, e.getMessage());
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

        // 1. Texto de bienvenida. Se rota entre diez variantes sin repetir la anterior.
        // Los documentos van adjuntos, no como enlaces.
        String mensaje = mensajesVenta.siguiente(nombreEntidad);

        log.info("[WHATSAPP] Inicio paquete venta inscripcion={} celular={} reciboBytes={} credenciales={} baseUrl={}",
                inscripcionId, celular, reciboPdf == null ? 0 : reciboPdf.length,
                credencialesPng == null ? 0 : credencialesPng.size(), baseUrl);

        boolean textoOk = enviarTexto(celular, mensaje);
        log.info("[WHATSAPP] Texto bienvenida inscripcion={} enviado={}", inscripcionId, textoOk);

        // 2. Recibo PDF
        if (reciboPdf != null && reciboPdf.length > 0) {
            boolean reciboOk = enviarDocumento(celular, reciboPdf, "recibo-" + inscripcionId + ".pdf",
                    "📄 Recibo de compra - Inscripción #" + inscripcionId);
            log.info("[WHATSAPP] Recibo inscripcion={} enviado={}", inscripcionId, reciboOk);
        }

        // 3. Credenciales virtuales (una por responsable), iguales a las descargables.
        if (credencialesPng != null) {
            for (int i = 0; i < credencialesPng.size(); i++) {
                byte[] credencial = credencialesPng.get(i);
                if (credencial != null && credencial.length > 0) {
                    boolean credencialOk = enviarImagen(celular, credencial,
                            "credencial-" + inscripcionId + "-" + (i + 1) + ".png",
                            "🎫 Credencial #" + (i + 1) + " - Inscripción #" + inscripcionId);
                    log.info("[WHATSAPP] Credencial inscripcion={} indice={} bytes={} enviada={}",
                            inscripcionId, i + 1, credencial.length, credencialOk);
                }
            }
        }
        log.info("[WHATSAPP] Fin paquete venta inscripcion={}", inscripcionId);
    }

    private record Payload(String number, String text) {
    }

    private record DocumentoPayload(String number, String mediatype, String mimetype,
                                    String caption, String media, String fileName) {
    }
}
