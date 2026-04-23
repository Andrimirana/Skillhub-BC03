package com.example.auth.dto;

/**
 * DTO représentant les informations publiques d'un utilisateur Skillhub.
 *
 * @param id    identifiant unique
 * @param nom   nom complet
 * @param email adresse email
 * @param role  rôle : formateur ou apprenant
 */
public record UtilisateurInfo(Long id, String nom, String email, String role) {}
