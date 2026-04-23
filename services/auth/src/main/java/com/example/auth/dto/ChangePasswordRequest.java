package com.example.auth.dto;

/**
 * DTO de la requête PUT /api/auth/change-password (TP5).
 *
 * <p>Requiert un header {@code Authorization: Bearer <token>} valide.
 * L'ancien mot de passe est vérifié par déchiffrement AES-GCM avant modification.</p>
 *
 * @param oldPassword     mot de passe actuel de l'utilisateur
 * @param newPassword     nouveau mot de passe souhaité
 * @param confirmPassword confirmation du nouveau mot de passe
 */
public record ChangePasswordRequest(String oldPassword, String newPassword, String confirmPassword) {}

