package com.usic.uniFex.Config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer{
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Carpeta donde guardas los comprobantes (misma que usas al subir)
        Path base = Paths.get("uploads/comprobantes").toAbsolutePath().normalize();

        // MUY IMPORTANTE: usar toUri().toString() para construir file:///
        String location = base.toUri().toString();   // e.g. file:///C:/.../uploads/comprobantes/

        registry.addResourceHandler("/files/comprobantes/**")
                .addResourceLocations(location);
    }
}
