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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuration Spring Security — mode stateless total avec CORS activé.
 *
 * <p>CSRF désactivé intentionnellement : l'API est stateless (aucune session HTTP,
 * aucun cookie de session). L'authentification passe uniquement par un header
 * {@code Authorization: Bearer <token>}, ce qui rend l'attaque CSRF impossible.</p>
 *
 * <p>CORS configuré pour autoriser les appels depuis le frontend React
 * (toute origine acceptée en développement).</p>
 *
 * @author  Équipe SkillHub BC04
 * @version 1.0
 */
// @Configuration : déclare cette classe comme source de beans Spring
//                  (les méthodes annotées @Bean produisent des objets gérés par Spring).
@Configuration
// @EnableWebSecurity : active la configuration de Spring Security pour l'application.
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Chaîne de filtres de sécurité.
     *
     * @param http builder Spring Security injecté par Spring
     * @return la chaîne de filtres construite
     * @throws Exception si la configuration Spring Security échoue
     */
    // @Bean : la valeur retournée devient un bean Spring nommé "filterChain".
    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // On active CORS pour autoriser les appels depuis le frontend React.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // On désactive CSRF car l'API est stateless (pas de cookie de session).
            .csrf(AbstractHttpConfigurer::disable)
            // Aucune session HTTP n'est créée : tout passe par le JWT.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // En-têtes de sécurité standards.
            .headers(headers -> headers
                .contentTypeOptions(contentType -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer ->
                    referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                            .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            // Toutes les routes sont accessibles : la vérification JWT se fait dans les contrôleurs.
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Configuration CORS — autorise le frontend React (toute origine, tous headers).
     * Intentionnel en développement : API stateless Bearer token, CORS inoffensif sans credentials.
     *
     * @return la source de configuration CORS appliquée à toutes les routes
     */
    // @Bean : la source CORS est exposée comme bean Spring et utilisée par filterChain().
    @Bean
    @SuppressWarnings("java:S5122")
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // En développement, on autorise toutes les origines.
        config.setAllowedOriginPatterns(List.of("*"));
        // On autorise les principales méthodes HTTP.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // On accepte tous les en-têtes envoyés par le client.
        config.setAllowedHeaders(List.of("*"));
        // Pas de cookies : l'authentification passe par un Bearer token.
        config.setAllowCredentials(false);
        // On applique cette config à toutes les routes.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Encodeur BCrypt — utilisable comme bean si un endpoint a besoin de hasher un mot de passe.
     *
     * @return un encodeur BCrypt prêt à l'emploi
     */
    // @Bean : Spring expose ce BCryptPasswordEncoder comme singleton injectable partout.
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Bean Spring : disponible si on a besoin d'un encodeur BCrypt ailleurs.
        return new BCryptPasswordEncoder();
    }
}
