package com.example.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration Spring Security — mode stateless total.
 *
 * <p>CSRF désactivé intentionnellement : l'API est stateless (aucune session HTTP,
 * aucun cookie de session). L'authentification passe uniquement par un header
 * {@code Authorization: Bearer <token>}, ce qui rend l'attaque CSRF impossible.</p>
 *
 * <p>En-têtes de sécurité HTTP activés : X-Content-Type-Options, X-Frame-Options,
 * HSTS et Referrer-Policy.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Chaîne de filtres de sécurité.
     *
     * @param http le builder HttpSecurity Spring
     * @return la SecurityFilterChain configurée
     * @throws Exception si la configuration échoue
     */
    @Bean
    @SuppressWarnings("java:S4502") // CSRF désactivé volontairement — API stateless Bearer token
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentTypeOptions(contentType -> {})
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(referrer ->
                    referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                            .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Encodeur BCrypt — utilisé pour les tests de politique de mot de passe.
     *
     * @return un BCryptPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

