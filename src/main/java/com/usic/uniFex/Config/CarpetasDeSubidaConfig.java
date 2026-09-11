package com.usic.uniFex.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Crea las carpetas de subida al arrancar, antes de que entre la primera peticion.
 *
 * Por que hace falta: la carpeta temporal de multipart la tiene que encontrar Tomcat ya creada.
 * Ni Spring ni Tomcat la crean, y si no existe **toda** subida falla con
 * {@code The temporary upload location [...] is not valid} — un error que ademas llega al cliente
 * como un 302 al login, porque la excepcion acaba en /error y ahi la cadena web la redirige. Eso
 * hacia imposible adjuntar la foto de un responsable, el comprobante del banco o la foto de una
 * caseta, y el sintoma no se parecia en nada a la causa.
 *
 * {@code FileStorageService} ya crea la carpeta de destino cuando guarda, pero para entonces la
 * peticion multipart ya se rompio: por eso la comprobacion va aqui y no alli.
 *
 * Va en un {@code @PostConstruct} y no en el ApplicationRunner a proposito: el runner corre
 * DESPUES de que el servidor web empiece a aceptar peticiones, asi que dejaria una ventana en la
 * que una subida temprana seguiria fallando.
 */
@Configuration
public class CarpetasDeSubidaConfig {

    private static final Logger logger = LoggerFactory.getLogger(CarpetasDeSubidaConfig.class);

    @Value("${app.upload-root:uploads}")
    private String raizDeSubidas;

    @Value("${spring.servlet.multipart.location:}")
    private String carpetaTemporal;

    @PostConstruct
    public void prepararCarpetasDeSubida() {
        asegurar(raizDeSubidas, "raiz de subidas (app.upload-root)");
        asegurar(carpetaTemporal, "temporal de multipart (spring.servlet.multipart.location)");
    }

    /**
     * No lanza si no puede crearla. En produccion la carpeta la prepara el despliegue con sus
     * permisos, y tumbar el arranque entero por esto dejaria el sistema sin ventas por un fallo
     * que solo afecta a los adjuntos. Se avisa con un WARN que dice exactamente que ruta fallo.
     */
    private void asegurar(String ruta, String para) {
        if (ruta == null || ruta.isBlank()) return;
        try {
            Path p = Paths.get(ruta);
            if (Files.isDirectory(p)) return;
            Files.createDirectories(p);
            logger.info("Carpeta creada, {}: {}", para, p.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            logger.warn("No se pudo crear la carpeta {} en '{}': {}. Las subidas de archivos "
                    + "fallaran hasta que exista y sea escribible.", para, ruta, e.getMessage());
        }
    }
}
