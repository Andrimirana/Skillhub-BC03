
package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée Spring Boot , Serveur d'authentification HMAC-SHA256.
 *
 * <p>Ce serveur implémente progressivement (TP1 → TP5) une authentification
 * REST sécurisée avec protocole HMAC-SHA256, nonce anti-rejeu,
 * chiffrement AES-256-GCM des mots de passe et changement de mot de passe.</p>
 *
 * <p> Ce projet est pédagogique.</p>
 *
 * @author  Équipe SkillHub BC04
 * @version 1.0
 */
// @SpringBootApplication = méta-annotation Spring Boot qui combine :
//   - @Configuration       : déclare cette classe comme source de beans Spring
//   - @EnableAutoConfiguration : active la configuration automatique (DataSource, MVC, JPA…)
//   - @ComponentScan       : scanne le package com.example.auth pour détecter @Service, @RestController, @Repository
@SpringBootApplication
public class AuthApplication {

    /**
     * Méthode principale — démarre le serveur Spring Boot 
     *
     * @param args arguments en ligne de commande passés à la JVM
     */
    public static void main(String[] args) {
        // Démarre le serveur Spring Boot du service Auth.
        SpringApplication.run(AuthApplication.class, args);
    }
}

