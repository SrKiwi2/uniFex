package com.usic.uniFex.Config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Convierte cualquier excepcion no controlada de un {@code @RestController} en un 500 con
 * cuerpo JSON, en vez de dejar que Spring la mande al forward interno {@code /error}.
 *
 * Por que existe: sin esto, una excepcion en {@code /api/app/**} terminaba en {@code /error},
 * que lo atiende la cadena de seguridad web (la de Thymeleaf) y responde con un **302 al
 * login**. Desde el navegador, ese 302 apunta al backend (otro origen que la SPA en dev),
 * asi que el navegador lo bloquea y el desarrollador solo ve "CORS Missing Allow Origin" y
 * "NetworkError" — sin ni una pista del fallo real. Paso de verdad: una
 * {@code NullPointerException} en "Mis ventas" se presento durante un buen rato como un
 * problema de CORS.
 *
 * Es el mismo motivo por el que los manejadores 401/403 de {@code SecurityConfig} escriben
 * el JSON a mano con {@code setStatus} en lugar de usar {@code sendError}.
 *
 * Alcance: solo clases anotadas con {@code @RestController}. Las vistas Thymeleaf conservan
 * su pagina de error de siempre.
 */
@RestControllerAdvice(annotations = RestController.class)
@Slf4j
public class ManejadorErroresApi {

    /**
     * Peticion mal formada por el cliente (un {@code ?edicion=abc}, un cuerpo JSON invalido).
     * Va aparte porque no es un fallo del servidor: devolver 500 aqui mandaria a buscar el
     * problema en el sitio equivocado.
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
    })
    public ResponseEntity<Map<String, Object>> peticionInvalida(Exception e, HttpServletRequest peticion) {
        log.warn("Peticion invalida en {} {}: {}", peticion.getMethod(), peticion.getRequestURI(), e.getMessage());
        return cuerpo(HttpStatus.BAD_REQUEST, "Peticion invalida", e, peticion);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> fallo(Exception e, HttpServletRequest peticion) {
        // Se registra con traza completa: es la unica copia del error, porque al cliente
        // solo se le manda el mensaje.
        log.error("Fallo no controlado en {} {}", peticion.getMethod(), peticion.getRequestURI(), e);
        return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno procesando la peticion", e, peticion);
    }

    private ResponseEntity<Map<String, Object>> cuerpo(HttpStatus estado, String mensaje,
                                                       Exception e, HttpServletRequest peticion) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("mensaje", mensaje);
        m.put("detalle", e.getClass().getSimpleName() + ": " + e.getMessage());
        m.put("ruta", peticion.getRequestURI());
        return ResponseEntity.status(estado).body(m);
    }
}
