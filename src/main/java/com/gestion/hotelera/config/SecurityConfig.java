package com.gestion.hotelera.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!test")
public class SecurityConfig {

        private final AuthenticationProvider authenticationProvider;
        private final RateLimitingFilter rateLimitingFilter;
        private final com.gestion.hotelera.security.JwtAuthenticationFilter jwtAuthenticationFilter;

        public SecurityConfig(
                        AuthenticationProvider authenticationProvider,
                        RateLimitingFilter rateLimitingFilter,
                        com.gestion.hotelera.security.JwtAuthenticationFilter jwtAuthenticationFilter) {
                this.authenticationProvider = authenticationProvider;
                this.rateLimitingFilter = rateLimitingFilter;
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        }

        @org.springframework.core.annotation.Order(1)
        @Bean
        public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
                return http
                                .securityMatcher("/api/**")
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authenticationProvider(authenticationProvider)
                                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/auth/**", "/api/resenas/aprobadas/**")
                                                .permitAll()
                                                .requestMatchers("/api/habitaciones/**")
                                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_RECEPCIONISTA", "ROLE_CLIENTE")
                                                .requestMatchers("/api/reservas/**")
                                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_RECEPCIONISTA", "ROLE_CLIENTE")
                                                .requestMatchers("/api/servicios/**")
                                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_RECEPCIONISTA", "ROLE_CLIENTE")
                                                .requestMatchers("/api/descuentos/**").hasAuthority("ROLE_ADMIN")
                                                .anyRequest().authenticated())
                                .build();
        }

        @org.springframework.core.annotation.Order(2)
        @Bean
        public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
                return http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(csrf -> csrf.disable())
                                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                                .authorizeHttpRequests(auth -> auth
                                                // RUTAS PÚBLICAS
                                                .requestMatchers("/", "/index", "/home", "/login", "/registro",
                                                                "/logout",
                                                                "/css/**", "/js/**", "/images/**",
                                                                "/h2-console/**",
                                                                "/habitaciones/publico", "/resenas/publico",
                                                                "/actuator/health", "/actuator/info")
                                                .permitAll()

                                                // ACTUATOR - Solo ADMIN puede ver endpoints sensibles
                                                .requestMatchers("/actuator/**")
                                                .hasAuthority("ROLE_ADMIN")

                                                // ADMIN - Funciones exclusivas
                                                .requestMatchers("/empleados/**", "/admin/**", "/auditoria/**",
                                                                "/descuentos/**", "/resenas/pendientes", "/reportes/**",
                                                                "/monitoreo/**")
                                                .hasAuthority("ROLE_ADMIN")

                                                // CLIENTE - Rutas específicas (RELAJADO PARA DEBUG)
                                                .requestMatchers("/cliente/**", "/resenas/crear",
                                                                "/resenas/mis-resenas")
                                                .authenticated()

                                                // RECEPCIONISTA o ADMIN: gestión operativa
                                                .requestMatchers("/recepcion/**", 
                                                                "/reservas/checkin/**", 
                                                                "/reservas/checkout/**")
                                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_RECEPCIONISTA")
                                                // DASHBOARD (requiere login)
                                                .requestMatchers("/dashboard")
                                                .authenticated()

                                                // Cualquier otra ruta requiere autenticación
                                                .anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .authenticationProvider(authenticationProvider)
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .successHandler(roleBasedSuccessHandler())
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/?logout=true")
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true)
                                                .deleteCookies("JSESSIONID")
                                                .permitAll())
                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.sameOrigin())
                                                .contentTypeOptions(content -> {
                                                })
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .maxAgeInSeconds(31536000)
                                                                .includeSubDomains(true)))
                                .build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:*"));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("*"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/api/**", configuration);
                return source;
        }

        @Bean
        public AuthenticationSuccessHandler roleBasedSuccessHandler() {
                return (request, response, authentication) -> {
                        boolean isStaff = authentication.getAuthorities().stream()
                                        .map(GrantedAuthority::getAuthority)
                                        .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ROLE_RECEPCIONISTA".equals(a));
                        boolean isClient = authentication.getAuthorities().stream()
                                        .map(GrantedAuthority::getAuthority)
                                        .anyMatch(a -> "ROLE_CLIENTE".equals(a));

                        if (isStaff) {
                                response.sendRedirect("/dashboard?loginSuccess=true");
                        } else if (isClient) {
                                response.sendRedirect("/?loginSuccess=true");
                        } else {
                                response.sendRedirect("/?loginSuccess=true");
                        }
                };
        }
}
