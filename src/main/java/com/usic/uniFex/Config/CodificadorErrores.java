package com.usic.uniFex.Config;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.encoder.EncoderBase;

/** Texto UTF-8: un objeto JSON por linea, incluso cuando la traza tiene saltos de linea. */
public class CodificadorErrores extends EncoderBase<ILoggingEvent> {
    private final ObjectMapper json = new ObjectMapper();
    private static final Pattern SECRETOS = Pattern.compile(
            "(?i)([\"']?(?:password|contrasena|contraseña|token|secret|authorization|api[_-]?key)[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s,;}]+)");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    public static String limpiar(String texto, int limite) {
        if (texto == null) return "";
        String limpio = JWT.matcher(texto).replaceAll("[oculto]");
        limpio = limpio.replaceAll("(?i)Bearer\\s+[^\\s,;\"']+", "Bearer [oculto]");
        limpio = SECRETOS.matcher(limpio).replaceAll("$1[oculto]");
        return limpio.length() > limite ? limpio.substring(0, limite) + "…" : limpio;
    }

    @Override public byte[] encode(ILoggingEvent evento) {
        var mdc = evento.getMDCPropertyMap();
        var registro = new LinkedHashMap<String, Object>();
        registro.put("fecha", Instant.ofEpochMilli(evento.getTimeStamp()).toString());
        registro.put("usuario", limpiar(mdc.getOrDefault("usuario", "Sistema"), 150));
        registro.put("usuarioId", mdc.getOrDefault("usuarioId", ""));
        registro.put("peticionId", mdc.getOrDefault("peticionId", ""));
        registro.put("ruta", limpiar(mdc.getOrDefault("ruta", ""), 500));
        registro.put("origen", limpiar(mdc.getOrDefault("origen", "SERVIDOR"), 40));
        registro.put("modulo", evento.getLoggerName());
        registro.put("mensaje", limpiar(evento.getFormattedMessage(), 8000));
        registro.put("detalle", limpiar(evento.getThrowableProxy() == null ? ""
                : ThrowableProxyUtil.asString(evento.getThrowableProxy()), 24000));
        try {
            return (json.writeValueAsString(registro) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            addError("No se pudo codificar el registro de error", e);
            return new byte[0];
        }
    }
    @Override public byte[] headerBytes() { return new byte[0]; }
    @Override public byte[] footerBytes() { return new byte[0]; }
}
