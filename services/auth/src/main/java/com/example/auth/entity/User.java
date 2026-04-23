package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant un utilisateur enregistré.
 *
 * <p>Le champ {@code passwordEncrypted} contient le mot de passe chiffré via AES-256-GCM
 * avec la {@code APP_MASTER_KEY}. Le format de stockage est :
 * {@code v1:Base64(iv):Base64(ciphertext)}</p>
 *
 * <p>Les champs {@code failedAttempts} et {@code lockUntil} implémentent
 * la protection anti brute-force : après 5 échecs, le compte est bloqué 2 minutes.</p>
 *
 * <p> Cette implémentation est pédagogique. Ne jamais utiliser en production
 * sans audit de sécurité complet.</p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    /** Identifiant technique auto-généré. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom complet de l'utilisateur (optionnel — ajouté pour Skillhub). */
    @Column(nullable = true)
    private String name;

    /** Rôle de l'utilisateur : formateur ou apprenant (ajouté pour Skillhub). */
    @Column(nullable = true)
    private String role = "apprenant";

    /** Adresse email — identifiant métier unique de l'utilisateur. */
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * Mot de passe chiffré AES-256-GCM.
     * Format : {@code v1:Base64(iv):Base64(ciphertext)}.
     * Déchiffré par {@code MasterKeyService} lors du login pour recalculer le HMAC.
     */
    @Column(name = "password_encrypted", nullable = false)
    private String passwordEncrypted;

    /** Nombre de tentatives de connexion échouées consécutives (anti brute-force). */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    /** Date jusqu'à laquelle le compte est verrouillé. Null si non verrouillé. */
    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    /** Date de création du compte. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Constructeur de création d'un utilisateur.
     *
     * @param email             adresse email unique
     * @param passwordEncrypted mot de passe chiffré AES-256-GCM
     */
    public User(String email, String passwordEncrypted) {
        this.email             = email;
        this.passwordEncrypted = passwordEncrypted;
        this.failedAttempts    = 0;
        this.createdAt         = LocalDateTime.now();
    }
}

