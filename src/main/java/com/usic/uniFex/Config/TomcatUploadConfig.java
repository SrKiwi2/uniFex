package com.usic.uniFex.Config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatUploadConfig {
    @Bean
    public TomcatServletWebServerFactory tomcatFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addContextCustomizers(ctx -> {
            ctx.addParameter("org.apache.tomcat.util.http.fileupload.FileUploadBase.fileCountMax", "5000");
            String v = ctx.findParameter("org.apache.tomcat.util.http.fileupload.FileUploadBase.fileCountMax");
            System.out.println(">> fileCountMax en Tomcat = " + v);
        });
        return factory;
    }
}
