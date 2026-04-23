package com.example.auth.service;

import com.example.auth.entity.AccessToken;
import com.example.auth.entity.User;
import com.example.auth.exception.AuthenticationFailedException;
import com.example.auth.repository.AccessTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service de gestion des tokens d'accès Bearer (SSO).
 *
 * <p>Émet un token UUID après chaque login réussi et le valide
 * lors des accès aux endpoints protégés.</p>
 *
 * <p>Chaque token est valide pendant {@value #TOKEN_VALIDITY_MINUTES} minutes.</p>
 *
 * <p> Ce token simple UUID est pédagogique. En production, on utiliserait
 * un JWT signé (RS256/HS256) pour éviter la requête DB à chaque validation.</p>
 */
@Service
public class TokenService {

    /** Durée de validité d'un token en minutes. */
    public static final int TOKEN_VALIDITY_MINUTES = 15;

    private final AccessTokenRepository accessTokenRepository;

    public TokenService(AccessTokenRepository accessTokenRepository) {
        this.accessTokenRepository = accessTokenRepository;
    }

    /**
     * Génère un nouveau token Bearer pour un utilisateur authentifié.
     * Le token est persisté en base de données.
     *
     * @param user l'utilisateur authentifié
     * @return le token d'accès créé
     */
    @Transactional
    public AccessToken generate(User user) {
        String        tokenValue = UUID.randomUUID().toString();
        LocalDateTime expiresAt  = LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES);
        AccessToken   token      = new AccessToken(user, tokenValue, expiresAt);
        return accessTokenRepository.save(token);
    }

    /**
     * Recherche l'utilisateur associé à un token Bearer valide.
     *
     * @param tokenValue la valeur UUID du token
     * @return l'utilisateur propriétaire du token
     * @throws AuthenticationFailedException si le token est introuvable ou expiré
     */
    @Transactional(readOnly = true)
    public User getUserByToken(String tokenValue) {
        AccessToken token = accessTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new AuthenticationFailedException(
                    "Token invalide ou expiré"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthenticationFailedException("Token expiré");
        }
        return token.getUser();
    }

    /**
     * Supprime un token de la base de données (déconnexion — ajouté pour Skillhub).
     *
     * @param tokenValue la valeur UUID du token à invalider
     */
    @Transactional
    public void deleteToken(String tokenValue) {
        accessTokenRepository.deleteByToken(tokenValue);
    }
}

