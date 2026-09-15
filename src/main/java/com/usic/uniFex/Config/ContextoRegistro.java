package com.usic.uniFex.Config;

import org.slf4j.MDC;

/** Conserva el usuario de la accion cuando su trabajo continua en otro hilo. */
public final class ContextoRegistro {
    private ContextoRegistro() {}

    public static Runnable conservar(Runnable tarea) {
        var contexto = MDC.getCopyOfContextMap();
        return () -> {
            var anterior = MDC.getCopyOfContextMap();
            try {
                if (contexto == null) MDC.clear(); else MDC.setContextMap(contexto);
                tarea.run();
            } finally {
                if (anterior == null) MDC.clear(); else MDC.setContextMap(anterior);
            }
        };
    }
}
