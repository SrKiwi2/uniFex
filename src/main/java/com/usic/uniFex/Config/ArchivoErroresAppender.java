package com.usic.uniFex.Config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;

/** Comparte el bloqueo con la descarga y el vaciado para no cortar una linea en escritura. */
public class ArchivoErroresAppender extends RollingFileAppender<ILoggingEvent> {
    @Override
    public synchronized void doAppend(ILoggingEvent evento) {
        super.doAppend(evento);
    }
}
