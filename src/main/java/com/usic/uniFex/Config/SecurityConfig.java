package com.usic.uniFex.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import com.usic.uniFex.security.JwtAuthFilter;
import com.usic.uniFex.security.JwtService;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Dos cadenas: la del API (JWT, stateless) y la del sitio Thymeleaf (sesion).
 *
 * {@code @EnableMethodSecurity} habilita {@code @PreAuthorize} en los controladores del API.
 * Sin el, las anotaciones se ignoran EN SILENCIO y el endpoint queda abierto a cualquier
 * usuario autenticado, que es justo lo contrario de lo que aparenta el codigo.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public org.springframework.web.filter.ForwardedHeaderFilter forwardedHeaderFilter() {
        return new org.springframework.web.filter.ForwardedHeaderFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS del API, para el APK.
     *
     * Los origenes son los que usa Capacitor en el WebView (`https://localhost` en Android,
     * `capacitor://localhost` en iOS) mas los de desarrollo. Se listan explicitamente en vez
     * de poner "*" porque con credenciales el comodin no es valido, y porque un API que
     * acepta cualquier origen invita a que cualquier pagina web haga peticiones en nombre
     * del usuario.
     *
     * `X-Origen` va en las cabeceras permitidas: es la que usa la auditoria para distinguir
     * WEB de APK, y sin declararla el navegador la bloquea en la peticion previa (preflight).
     */
    private CorsConfigurationSource corsApi() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "https://localhost",        // APK Android (Capacitor)
                "capacitor://localhost",    // iOS, si algun dia se compila
                "http://localhost:5173",    // SPA en desarrollo
                "http://localhost:7676"));  // la propia app servida por Spring
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Origen"));
        config.setMaxAge(3600L); // cachea el preflight una hora: menos ida y vuelta en movil

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Cadena 1 (Fase 2): API movil/SPA. Stateless, autenticacion por JWT.
     * Solo aplica a /api/auth/** (login, publico) y /api/app/** (protegido).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                // AntPathRequestMatcher (ruta pura) evita una peculiaridad del MvcRequestMatcher
                // con /** que dejaba GET /api/app/puestos fuera de esta cadena.
                .securityMatcher(new OrRequestMatcher(
                        AntPathRequestMatcher.antMatcher("/api/auth/**"),
                        AntPathRequestMatcher.antMatcher("/api/app/**")))
                .csrf(csrf -> csrf.disable())
                // CORS solo en esta cadena: la web navegada no lo necesita (mismo origen),
                // pero el APK si. Capacitor sirve la app desde https://localhost en Android,
                // que para el backend es un ORIGEN DISTINTO; sin esto el navegador embebido
                // bloquea hasta el login y el fallo se ve como un error de red sin causa.
                .cors(cors -> cors.configurationSource(corsApi()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/api/auth/**")).permitAll()
                        .anyRequest().authenticated())
                // API: responder con codigos HTTP (401/403) y cuerpo JSON, no redirigir a un
                // login. Se usa setStatus + write (no sendError) para no disparar el forward
                // interno a /error, que la cadena web volveria a redirigir con un 302.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"ok\":false,\"mensaje\":\"No autenticado\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"ok\":false,\"mensaje\":\"Sin permiso\"}");
                        }))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Cadena 2: sitio web Thymeleaf. Autenticacion por sesion (comportamiento actual).
     * Aplica a todo lo que no capturo la cadena 1.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                auth -> auth.requestMatchers(
                        "/",
                        "/control-responsable",
                        "/buscar-responsable",
                        "/vistaR/**",
                        "/admin/**",
                        "/iniciar-sesion/**",
                        "/acceso/**",
                        "/ver/**",
                        "/api/**",
                        "/venta/**",
                        "/guardar/**",
                        "/actualizar/**",
                        "/boletos/**",
                        "/files/**",
                        "/inscripciones/**",
                        "/ws/**",
                        "/cerrar_sesion",
                        "/administracion/**",
                        "/vistaGenerarCredenciales/**",
                        "/assets/**").permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(login -> login
                        .loginPage("/")
                        .permitAll())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

}
