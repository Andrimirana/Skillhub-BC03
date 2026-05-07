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

    // Clé secrète pour signer le JWT (à externaliser en prod)
    @Value("${jwt.secret:dev-secret-key-please-change}")
    private String jwtSecret;

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
        // On génère un identifiant unique (UUID) qui servira de token.
        String tokenValue = UUID.randomUUID().toString();
        // Le token est valable 15 minutes.
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES);
        // On enregistre le token en base associé à l'utilisateur.
        AccessToken token = new AccessToken(user, tokenValue, expiresAt);
        return accessTokenRepository.save(token);
    }

    /**
     * Génère un JWT signé HS256 contenant les claims nécessaires aux middlewares Laravel.
     * Claims embarqués : sub (email), userId, nom, role — valides TOKEN_VALIDITY_MINUTES minutes.
     *
     * @param user l'utilisateur authentifié
     * @return le JWT signé
     */
    public String generateJwt(User user) {
        // Date courante et date d'expiration (now + 15 minutes).
        Date now = new Date();
        Date expiry = new Date(now.getTime() + TOKEN_VALIDITY_MINUTES * 60 * 1000L);
        // On construit le JWT avec les infos de l'utilisateur dans les claims.
        return Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .claim("userId", user.getId())
                .claim("nom",  user.getName()  != null ? user.getName()  : user.getEmail())
                .claim("role", user.getRole()  != null ? user.getRole()  : "apprenant")
                // On signe le JWT avec l'algorithme HS256.
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Valide la signature d'un JWT et retourne les claims utilisateur.
     * Utilisé par {@code POST /api/validate-token} pour les appels inter-services.
     *
     * @param jwt le token JWT Bearer reçu
     * @return map {id, nom, email, role} extraite des claims
     * @throws io.jsonwebtoken.JwtException si le token est invalide ou expiré
     */
    public java.util.Map<String, Object> validateJwtClaims(String jwt) {
        // On vérifie la signature et on extrait les claims (le contenu du JWT).
        io.jsonwebtoken.Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(jwt)
                .getBody();

        // On construit la map de réponse à renvoyer aux microservices.
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("id",    claims.get("userId"));
        result.put("nom",   claims.getOrDefault("nom",  claims.getSubject()));
        result.put("email", claims.getSubject());
        result.put("role",  claims.getOrDefault("role", "apprenant"));
        return result;
    }

    /**
     * Dérive une clé HMAC-SHA256 de 256 bits depuis jwtSecret via SHA-256.
     * Garantit une clé valide quelle que soit la longueur du secret configuré.
     */
    private Key getSigningKey() {
        try {
            // On dérive une clé de 256 bits depuis le secret via SHA-256.
            // Cela garantit que la clé est toujours valide pour HS256.
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(jwtSecret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
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
        // On cherche le token en base. S'il n'existe pas, c'est un 401.
        AccessToken token = accessTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new AuthenticationFailedException(
                    "Token invalide ou expiré"));

        // On vérifie que le token n'est pas expiré.
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthenticationFailedException("Token expiré");
        }
        // Token valide : on retourne l'utilisateur associé.
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

