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
                .setCachePeriod(3600); // 1h cache opcional
    }
}
