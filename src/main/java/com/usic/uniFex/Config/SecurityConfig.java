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
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/",
            "/admin/**",
            "/iniciar-sesion/**",
            "/ver/**",
            "/cerrar_sesion",
            "/administracion/**",
            "/assets/**")
            .permitAll()
            .anyRequest().authenticated()
        )
        .formLogin(login -> login
            .loginPage("/")
            .permitAll()
        )
        .headers(headers -> headers
            .frameOptions(frame -> frame.sameOrigin())
        )
        .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
