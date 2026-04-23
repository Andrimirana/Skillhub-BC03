package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant un token d'accès Bearer émis après un login réussi.
 *
 * <p>Chaque token est un UUID unique valide 15 minutes. Il est transmis au client
 * dans la réponse de login et doit être inclus dans chaque requête suivante via
 * le header {@code Authorization: Bearer <token>}.</p>
 *
 * <p> Ce token simple UUID est pédagogique. En production, on utiliserait
 * un JWT signé (RS256/HS256) pour éviter la requête DB à chaque validation.</p>
 */
@Entity
@Table(name = "access_tokens")
@Getter
@Setter
@NoArgsConstructor
public class AccessToken {

    /** Identifiant technique auto-généré. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Propriétaire du token. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Valeur UUID du token Bearer. */
    @Column(unique = true, nullable = false)
    private String token;

    /** Date d'expiration du token (now + 15 minutes). */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Date de génération du token. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Constructeur de création d'un token d'accès.
     *
     * @param user      utilisateur propriétaire
     * @param token     valeur UUID du token
     * @param expiresAt date d'expiration
     */
    public AccessToken(User user, String token, LocalDateTime expiresAt) {
        this.user      = user;
        this.token     = token;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }
}

