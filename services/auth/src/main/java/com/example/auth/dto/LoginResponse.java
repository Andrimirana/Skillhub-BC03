package com.example.auth.dto;

import java.time.LocalDateTime;

/**
 * DTO de la réponse HTTP 200 après un login réussi.
 *
 * @param accessToken token UUID Bearer à inclure dans les requêtes suivantes
 * @param expiresAt   date/heure d'expiration du token (now + 15 minutes)
 */
public record LoginResponse(String accessToken, LocalDateTime expiresAt) {}

