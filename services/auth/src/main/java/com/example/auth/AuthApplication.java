package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée Spring Boot — Serveur d'authentification HMAC-SHA256.
 *
 * <p>Ce serveur implémente progressivement (TP1 → TP5) une authentification
 * REST sécurisée avec protocole HMAC-SHA256, nonce anti-rejeu,
 * chiffrement AES-256-GCM des mots de passe et changement de mot de passe.</p>
 *
 * <p> Ce projet est pédagogique. Ne jamais utiliser en production sans audit.</p>
 */
@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        // Démarre le serveur Spring Boot du service Auth.
        SpringApplication.run(AuthApplication.class, args);
    }
}

