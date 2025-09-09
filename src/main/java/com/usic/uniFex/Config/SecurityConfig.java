package com.usic.uniFex.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public org.springframework.web.filter.ForwardedHeaderFilter forwardedHeaderFilter() {
        return new org.springframework.web.filter.ForwardedHeaderFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/",
                "/vistaR/**",
                "/admin/**",            // <-- ojo: esto hoy queda público
                "/iniciar-sesion/**",
                "/ver/**",
                "/api/**",
                "/guardar/**",
                "/actualizar/**",
                "/files/**",            // <-- tus PDFs
                "/cerrar_sesion",
                "/administracion/**",
                "/assets/**")
            .permitAll()
            .anyRequest().authenticated()
        )
        .formLogin(login -> login
            .loginPage("/")                       // tu index con el modal
            .loginProcessingUrl("/iniciar-sesion")// <-- AQUÍ: procesa el POST del modal
            .usernameParameter("usuario")         // <-- nombres de tus inputs
            .passwordParameter("contrasena")
            .defaultSuccessUrl("/", true)         // vuelve al index tras loguear
            .failureUrl("/?error")                // puedes hacer que abra el modal si hay error
            .permitAll()
        )
        .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
        .csrf(csrf -> csrf.disable());

        return http.build();
    }

}
