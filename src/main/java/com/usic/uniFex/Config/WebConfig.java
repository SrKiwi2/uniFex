package com.usic.uniFex.Config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer{
    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    @Autowired
    private AutenticacionInterceptor autenticacionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Aplica @ValidarUsuarioAutenticado en los controladores del web.
        registry.addInterceptor(autenticacionInterceptor);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadRoot).toAbsolutePath().toUri().toString(); // e.g. file:/var/uniFex/uploads/
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location)
                // Un año, no una hora. Todo lo que sirve este handler lo escribio
                // FileStorageService.save, que le pone un UUID al nombre: un archivo nuevo
                // es SIEMPRE una URL nueva, y una URL dada nunca cambia de contenido. Con
                // una hora, el APK volvia a bajarse el plano (y las fotos de las casetas)
                // varias veces al dia, y en la feria eso son segundos de pantalla en blanco
                // con una red mala. Reemplazar el plano sigue viendose al instante porque
                // llega con otra ruta.
                .setCachePeriod(365 * 24 * 3600);
    }
}
