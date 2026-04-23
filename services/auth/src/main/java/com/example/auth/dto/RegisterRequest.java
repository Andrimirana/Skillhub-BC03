package com.example.auth.dto;

/**
 * DTO de la requête POST /api/auth/register.
 *
 * @param email           adresse email de l'utilisateur
 * @param password        mot de passe choisi
 * @param passwordConfirm confirmation du mot de passe
 */
public record RegisterRequest(String email, String password, String passwordConfirm) {}

