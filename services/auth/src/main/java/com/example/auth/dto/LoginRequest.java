package com.example.auth.dto;

/**
 * DTO de la requête POST /api/auth/login (protocole HMAC-SHA256).
 *
 * <p>Le mot de passe ne circule jamais sur le réseau.
 * Le champ {@code hmac} est la preuve cryptographique calculée par le client :
 * {@code HMAC_SHA256(key=password, data=email:nonce:timestamp)}</p>
 *
 * @param email     adresse email de l'utilisateur
 * @param nonce     UUID aléatoire généré par le client (anti-rejeu)
 * @param timestamp epoch Unix en secondes au moment de la requête
 * @param hmac      signature Base64 HMAC-SHA256
 */
public record LoginRequest(String email, String nonce, long timestamp, String hmac) {}

