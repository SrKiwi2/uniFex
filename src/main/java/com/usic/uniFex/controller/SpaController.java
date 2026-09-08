package com.usic.uniFex.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sirve la SPA Vue compilada bajo /app sin reemplazar el sitio Thymeleaf legacy.
 */
@Controller
public class SpaController {

    @GetMapping({
            "/app",
            "/app/",
            "/app/{ruta:[^\\.]*}",
            "/app/{ruta1:[^\\.]*}/{ruta2:[^\\.]*}",
            "/app/{ruta1:[^\\.]*}/{ruta2:[^\\.]*}/{ruta3:[^\\.]*}"
    })
    public String app() {
        return "forward:/app/index.html";
    }
}
