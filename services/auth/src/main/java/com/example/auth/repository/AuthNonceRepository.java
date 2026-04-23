package com.example.auth.repository;

import com.example.auth.entity.AuthNonce;
import com.example.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository JPA pour la gestion des nonces anti-rejeu.
 */
public interface AuthNonceRepository extends JpaRepository<AuthNonce, Long> {

    /**
     * Recherche un nonce pour un utilisateur donné.
     *
     * @param user  l'utilisateur
     * @param nonce la valeur du nonce
     * @return un Optional contenant le nonce s'il existe
     */
    Optional<AuthNonce> findByUserAndNonce(User user, String nonce);

    /**
     * Supprime tous les nonces expirés (purge périodique).
     *
     * @param now la date/heure courante
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AuthNonce n WHERE n.expiresAt < :now")
    void deleteExpiredNonces(LocalDateTime now);
}

