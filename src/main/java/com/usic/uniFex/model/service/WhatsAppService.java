package com.usic.uniFex.model.service;

import java.io.IOException;
import java.util.Base64;

import com.usic.uniFex.model.entity.InstanciaWhatsApp;
import okhttp3.HttpUrl;
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
 * <p>Usa la instancia activa administrada desde la SPA y guardada en la base de datos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final MensajesWhatsAppVenta mensajesVenta;

    private final InstanciaWhatsAppService instancias;

    public boolean habilitado() {
        return instancias.activa().isPresent();
    }

    private HttpUrl url(InstanciaWhatsApp configuracion, String operacion) {
        return HttpUrl.get(configuracion.getUrlApi()).newBuilder()
                .addPathSegment(operacion).addEncodedPathSegment(configuracion.getInstancia()).build();
    }

    /**
     * Envía un mensaje de texto simple.
     */
    public boolean enviarTexto(String numero, String mensaje) {
        return enviarTexto(instancias.activa().orElse(null), numero, mensaje);
    }

    private boolean enviarTexto(InstanciaWhatsApp configuracion, String numero, String mensaje) {
        if (configuracion == null) {
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
            Request request = new Request.Builder()
                    .url(url(configuracion, "sendText"))
                    .addHeader("apikey", configuracion.getClaveApi())
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
        return enviarMedia(instancias.activa().orElse(null), numero, pdfBytes, fileName, caption, "document", "application/pdf");
    }

    public boolean enviarImagen(String numero, byte[] imagenBytes, String fileName, String caption) {
        return enviarMedia(instancias.activa().orElse(null), numero, imagenBytes, fileName, caption, "image", "image/png");
    }

    private boolean enviarMedia(InstanciaWhatsApp configuracion, String numero, byte[] bytes, String fileName, String caption,
                                String mediatype, String mimetype) {
        if (configuracion == null) {
            log.debug("WhatsApp deshabilitado o sin configuración; no se envía archivo a {}", numero);
            return false;
        }
        if (numero == null || numero.isBlank() || bytes == null || bytes.length == 0) {
            log.warn("Parámetros inválidos para enviar archivo a {}", numero);
            return false;
        }

        try {
            log.info("[WHATSAPP] Enviando archivo numero={} fileName='{}' mediatype={} mimetype={} bytes={} url={}sendMedia/{}",
                    numero, fileName, mediatype, mimetype, bytes.length, configuracion.getUrlApi(), configuracion.getInstancia());
            var payload = new DocumentoPayload(numero, mediatype, mimetype,
                    caption != null ? caption : "", Base64.getEncoder().encodeToString(bytes), fileName);
            RequestBody requestBody = RequestBody.create(mapper.writeValueAsString(payload), JSON);

            Request request = new Request.Builder()
                    .url(url(configuracion, "sendMedia"))
                    .addHeader("apikey", configuracion.getClaveApi())
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
        enviarBienvenidaVentaConPdfs(celular, nombreEntidad, inscripcionId, reciboPdf,
                credencialesPng, baseUrl, java.util.List.of());
    }

    public record ReciboExtra(Long responsableId, String nombre, byte[] pdf) {}

    public void enviarBienvenidaVentaConPdfs(String celular, String nombreEntidad, Long inscripcionId,
                                             byte[] reciboPdf, java.util.List<byte[]> credencialesPng,
                                             String baseUrl, java.util.List<ReciboExtra> recibosExtra) {
        // Conserva la misma conexion para todo el paquete, aunque cambie la activa durante el envio.
        InstanciaWhatsApp configuracion = instancias.activa().orElse(null);
        if (configuracion == null) {
            log.debug("WhatsApp deshabilitado; solo se loguea la intención de envío a {}", celular);
            return;
        }

        // 1. Texto de bienvenida. Se rota entre diez variantes sin repetir la anterior.
        // Los documentos van adjuntos, no como enlaces.
        String mensaje = mensajesVenta.siguiente(nombreEntidad);

        log.info("[WHATSAPP] Inicio paquete venta inscripcion={} celular={} reciboBytes={} credenciales={} baseUrl={}",
                inscripcionId, celular, reciboPdf == null ? 0 : reciboPdf.length,
                credencialesPng == null ? 0 : credencialesPng.size(), baseUrl);

        boolean textoOk = enviarTexto(configuracion, celular, mensaje);
        log.info("[WHATSAPP] Texto bienvenida inscripcion={} enviado={}", inscripcionId, textoOk);

        // 2. Recibo PDF
        if (reciboPdf != null && reciboPdf.length > 0) {
            boolean reciboOk = enviarMedia(configuracion, celular, reciboPdf, "recibo-" + inscripcionId + ".pdf",
                    "📄 Recibo de compra - Inscripción #" + inscripcionId, "document", "application/pdf");
            log.info("[WHATSAPP] Recibo inscripcion={} enviado={}", inscripcionId, reciboOk);
        }

        for (ReciboExtra extra : recibosExtra) {
            enviarMedia(configuracion, celular, extra.pdf(), "recibo-extra-" + extra.responsableId() + ".pdf",
                    "Recibo de compra de credencial extra - " + extra.nombre(), "document", "application/pdf");
        }

        // 3. Credenciales virtuales (una por responsable), iguales a las descargables.
        if (credencialesPng != null) {
            for (int i = 0; i < credencialesPng.size(); i++) {
                byte[] credencial = credencialesPng.get(i);
                if (credencial != null && credencial.length > 0) {
                    boolean credencialOk = enviarMedia(configuracion, celular, credencial,
                            "credencial-" + inscripcionId + "-" + (i + 1) + ".png",
                            "🎫 Credencial #" + (i + 1) + " - Inscripción #" + inscripcionId, "image", "image/png");
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
