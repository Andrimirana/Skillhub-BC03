package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant un nonce (Number Used Once) anti-rejeu.
 *
 * <p>Chaque requête de login génère un UUID unique côté client. Ce nonce est
 * enregistré en base dès réception pour empêcher tout rejeu de la même requête,
 * même dans la fenêtre de validité du timestamp (±60 secondes).</p>
 *
 * <p>La contrainte unique {@code (user_id, nonce)} garantit qu'un même nonce
 * ne peut être utilisé deux fois par le même utilisateur.</p>
 *
 * <p>TTL : 120 secondes. Les nonces expirés peuvent être purgés périodiquement.</p>
 */
@Entity
@Table(
    name = "auth_nonce",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_nonce",
        columnNames = {"user_id", "nonce"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AuthNonce {

    /** Identifiant technique auto-généré. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Utilisateur émetteur du nonce. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Valeur UUID du nonce — unique par utilisateur. */
    @Column(nullable = false)
    private String nonce;

    /** Date d'expiration du nonce (now + 120 secondes). */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Marqueur de consommation — true une fois utilisé. */
    @Column(nullable = false)
    private boolean consumed = false;

    /** Date de création. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Constructeur de création d'un nonce.
     *
     * @param user      utilisateur émetteur
     * @param nonce     valeur UUID du nonce
     * @param expiresAt date d'expiration (now + 120s)
     */
    public AuthNonce(User user, String nonce, LocalDateTime expiresAt) {
        this.user      = user;
        this.nonce     = nonce;
        this.expiresAt = expiresAt;
        this.consumed  = false;
        this.createdAt = LocalDateTime.now();
    }
}

