package com.example.auth.dto;

/**
 * DTO de réponse après inscription ou connexion réussie (format Skillhub).
 *
 * @param token       token Bearer UUID à inclure dans les requêtes suivantes
 * @param tokenType   type du token (toujours "Bearer")
 * @param expiresAt   timestamp Unix d'expiration
 * @param utilisateur informations publiques de l'utilisateur
 */
public record SkillhubAuthResponse(
        String token,
        String tokenType,
        long expiresAt,
        UtilisateurInfo utilisateur
) {}
