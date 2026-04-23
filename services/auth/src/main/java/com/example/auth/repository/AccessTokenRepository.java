package com.example.auth.repository;

import com.example.auth.entity.AccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository JPA pour la gestion des tokens d'accès Bearer.
 */
public interface AccessTokenRepository extends JpaRepository<AccessToken, Long> {

    /**
     * Recherche un token par sa valeur UUID.
     *
     * @param token la valeur du token
     * @return un Optional contenant le token s'il existe
     */
    Optional<AccessToken> findByToken(String token);

    /**
     * Supprime tous les tokens expirés (purge périodique).
     *
     * @param now la date/heure courante
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AccessToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);

    /**
     * Supprime un token par sa valeur UUID (déconnexion — ajouté pour Skillhub).
     *
     * @param token la valeur du token à invalider
     */
    @Modifying
    @Transactional
    void deleteByToken(String token);
}

