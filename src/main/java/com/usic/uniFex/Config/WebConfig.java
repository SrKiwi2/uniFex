package com.usic.uniFex.Config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
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

    /**
     * ETag en las dos lecturas del mapa, que son las unicas grandes y las que mas se repiten.
     *
     * El cliente resincroniza a menudo —al reconectar, al volver del fondo, al sondear— y casi
     * siempre nada ha cambiado. Con el ETag, esas veces el servidor contesta **304 sin cuerpo**:
     * unos cientos de bytes en vez de la lista entera. En la feria, con la red que hay, esa es
     * la diferencia entre un mapa que responde y uno que se queda pensando.
     *
     * Se usa el filtro "shallow": el servidor SI construye la respuesta y luego la compara. No
     * ahorra trabajo de servidor, ahorra RED, que es donde esta el cuello de botella. Y por
     * construccion nunca miente: la etiqueta es un hash de la respuesta real, asi que no hay
     * forma de que diga "no cambio nada" cuando si cambio.
     *
     * Limitado a esas dos rutas a proposito: envolver toda la API obligaria a almacenar en
     * memoria cada respuesta para poder hashearla.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> filtroEtagMapa() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> reg =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        reg.addUrlPatterns("/api/app/puestos", "/api/app/puestos/asignaciones");
        reg.setName("etagMapa");
        return reg;
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
