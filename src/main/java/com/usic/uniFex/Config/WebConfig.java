package com.usic.uniFex.Config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebConfig implements WebMvcConfigurer{
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path base = Paths.get("uploads/comprobantes").toAbsolutePath().normalize();
        registry.addResourceHandler("/files/comprobantes/**")
                .addResourceLocations("file:" + base.toString() + "/");
    }
}
