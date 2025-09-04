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
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/", "/login", "/webjars/**", "/css/**", "/js/**", "/images/**").permitAll()
        .requestMatchers("/admin/**").authenticated()
        .anyRequest().authenticated()
      )
      .formLogin(login -> login
        .loginPage("/login")                // GET: muestra la vista de login (pública)
        .loginProcessingUrl("/login")       // POST: procesa credenciales
        .defaultSuccessUrl("/admin", true)  // al loguear -> /admin
        .failureUrl("/login?error")         // si falla, vuelve con ?error
        .permitAll()
      )
      .logout(logout -> logout
        .logoutUrl("/logout")
        .logoutSuccessUrl("/login?logout")
        .permitAll()
      )
      // déjalo deshabilitado por ahora; si luego lo habilitas, agrega el token al form
      .csrf(csrf -> csrf.disable());

    return http.build();
  }
}
