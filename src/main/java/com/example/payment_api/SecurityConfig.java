
package com.example.payment_api;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of("http://localhost:[*]", "http://127.0.0.1:[*]", "http://192.168.*.*:[*]", "http://10.*.*.*:[*]"));

        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        config.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Receipt-Token")
        );

        config.setAllowCredentials(false);

        return request -> {

            String path = request.getRequestURI();

            // Easebuzz server callback
            if ("/api/payments/easebuzz/response".equals(path)) {
                return null;
            }

            // SabbPe server callback
            if ("/api/sabbpe/callback".equals(path)) {
                return null;
            }

            // Apply CORS configuration to API requests
            if (path.startsWith("/api/")) {
                return config;
            }

            return null;
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Registration and login
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login"
                        ).permitAll()

                        // SabbPe token endpoint for local testing
                        .requestMatchers(
                                "/api/sabbpe/token"
                        ).permitAll()

                        // SabbPe payment callback
                        .requestMatchers(
                                "/api/sabbpe/callback"
                        ).permitAll()

                        // Swagger documentation
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Existing Easebuzz callback
                        .requestMatchers(
                                "/api/payments/easebuzz/response"
                        ).permitAll()

                        // Public QR token validation only
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/split-sessions/validate"
                        ).permitAll()

                        // Public payment receipt status lookup
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/payments/*"
                        ).permitAll()

                        // Public payment receipt reconciliation (secured by receiptToken in controller)
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/sabbpe/reconciliation/payments/*/reconcile"
                        ).permitAll()

                        // SabbPe gateway callback
                        .requestMatchers(
                                "/api/sabbpe/gateway/callback"
                        ).permitAll()

                        // Public QR Payment Simulator customer endpoints
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/simulator/qr/details"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/simulator/qr/process"
                        ).permitAll()

                        // All remaining endpoints require authentication
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
