package com.example.auth.service;

import com.example.auth.entity.AccessToken;
import com.example.auth.entity.User;
import com.example.auth.exception.AuthenticationFailedException;
import com.example.auth.repository.AccessTokenRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * Service de gestion des jetons d'accès Bearer.
 *
 * <p>Émet un jeton UUID après chaque login réussi et le valide
 * lors des accès aux endpoints protégés.</p>
 *
 * <p>Chaque jeton est valide pendant {@value #DUREE_VALIDITE_MIN} minutes.</p>
 */
@Service
public class TokenService {


    /** Durée de validité d'un jeton en minutes. */
    public static final int DUREE_VALIDITE_MIN = 15;

    // Clé secrète pour signer le JWT (à externaliser en prod)
    @Value("${jwt.secret:dev-secret-key-please-change}")
    private String secretJwt;

    private final AccessTokenRepository depotJetons;

    public TokenService(AccessTokenRepository depotJetons) {
        this.depotJetons = depotJetons;
    }

    /**
     * Génère un nouveau jeton Bearer pour un utilisateur authentifié.
     * Le jeton est persisté en base de données.
     */
    @Transactional
    public AccessToken generate(User utilisateur) {
        // On génère un identifiant unique (UUID) qui servira de jeton.
        String valeurJeton = UUID.randomUUID().toString();
        // Le jeton est valable 15 minutes.
        LocalDateTime expireA = LocalDateTime.now().plusMinutes(DUREE_VALIDITE_MIN);
        // On enregistre le jeton en base associé à l'utilisateur.
        AccessToken jeton = new AccessToken(utilisateur, valeurJeton, expireA);
        return depotJetons.save(jeton);
    }

    /**
     * Génère un JWT signé HS256 contenant les claims nécessaires aux middlewares Laravel.
     * Claims embarqués : sub (email), userId, nom, role — valides 15 minutes.
     */
    public String generateJwt(User utilisateur) {
        // Date courante et date d'expiration (now + 15 minutes).
        Date maintenant = new Date();
        Date expiration = new Date(maintenant.getTime() + DUREE_VALIDITE_MIN * 60 * 1000L);
        // On construit le JWT avec les infos de l'utilisateur dans les claims.
        return Jwts.builder()
                .setSubject(utilisateur.getEmail())
                .setIssuedAt(maintenant)
                .setExpiration(expiration)
                .claim("userId", utilisateur.getId())
                .claim("nom",  utilisateur.getName() != null ? utilisateur.getName() : utilisateur.getEmail())
                .claim("role", utilisateur.getRole() != null ? utilisateur.getRole() : "apprenant")
                // On signe le JWT avec l'algorithme HS256.
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Valide la signature d'un JWT et retourne les claims utilisateur.
     * Utilisé par {@code POST /api/validate-token} pour les appels inter-services.
     */
    public java.util.Map<String, Object> validateJwtClaims(String jwt) {
        // On vérifie la signature et on extrait les claims (le contenu du JWT).
        io.jsonwebtoken.Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(jwt)
                .getBody();

        // On construit la map de réponse à renvoyer aux microservices.
        java.util.Map<String, Object> resultat = new java.util.HashMap<>();
        resultat.put("id",    claims.get("userId"));
        resultat.put("nom",   claims.getOrDefault("nom",  claims.getSubject()));
        resultat.put("email", claims.getSubject());
        resultat.put("role",  claims.getOrDefault("role", "apprenant"));
        return resultat;
    }

    /**
     * Dérive une clé HMAC-SHA256 de 256 bits depuis le secret JWT via SHA-256.
     * Garantit une clé valide quelle que soit la longueur du secret configuré.
     */
    private Key getSigningKey() {
        try {
            // On dérive une clé de 256 bits depuis le secret via SHA-256.
            // Cela garantit que la clé est toujours valide pour HS256.
            byte[] hachage = MessageDigest.getInstance("SHA-256")
                    .digest(secretJwt.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(hachage);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }

    /**
     * Recherche l'utilisateur associé à un jeton Bearer valide.
     */
    @Transactional(readOnly = true)
    public User getUserByToken(String valeurJeton) {
        // On cherche le jeton en base. S'il n'existe pas, c'est un 401.
        AccessToken jeton = depotJetons.findByToken(valeurJeton)
                .orElseThrow(() -> new AuthenticationFailedException(
                    "Token invalide ou expiré"));

        // On vérifie que le jeton n'est pas expiré.
        if (jeton.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthenticationFailedException("Token expiré");
        }
        // Jeton valide : on retourne l'utilisateur associé.
        return jeton.getUser();
    }

    /**
     * Supprime un jeton de la base de données (déconnexion).
     */
    @Transactional
    public void deleteToken(String valeurJeton) {
        depotJetons.deleteByToken(valeurJeton);
    }
}
