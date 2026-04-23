package com.example.auth.dto;

/**
 * DTO de la requête POST /api/register (format Skillhub).
 *
 * @param nom             nom complet de l'utilisateur
 * @param email           adresse email
 * @param password        mot de passe choisi
 * @param passwordConfirm confirmation du mot de passe
 * @param role            rôle : formateur ou apprenant
 */
public record SkillhubRegisterRequest(
        String nom,
        String email,
        String password,
        String passwordConfirm,
        String role
) {}
