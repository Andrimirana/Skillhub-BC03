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
 *
 * @author  Équipe SkillHub BC04
 * @version 1.0
 */
// @Service : bean Spring de la couche service, injecté dans AuthService.
@Service
public class TokenService {


    /** Durée de validité d'un jeton en minutes. */
    public static final int DUREE_VALIDITE_MIN = 15;

    // Clé secrète pour signer le JWT (à externaliser en prod)
    @Value("${jwt.secret:dev-secret-key-please-change}")
    private String secretJwt;

    private final AccessTokenRepository depotJetons;

    /**
     * Constructeur — injection de dépendance par Spring.
     *
     * @param depotJetons accès JPA aux jetons d'accès persistés
     */
    public TokenService(AccessTokenRepository depotJetons) {
        this.depotJetons = depotJetons;
    }

    /**
     * Génère un nouveau jeton Bearer pour un utilisateur authentifié.
     * Le jeton est persisté en base de données.
     *
     * @param utilisateur l'utilisateur propriétaire du jeton
     * @return l'entité {@link AccessToken} persistée (UUID + date d'expiration à +15 min)
     */
    // @Transactional : la création + persistance du jeton est encapsulée dans une transaction.
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
     *
     * @param utilisateur l'utilisateur pour lequel émettre le JWT
     * @return un JWT compact (3 segments base64url séparés par des points)
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
     *
     * @param jwt le JWT compact à valider
     * @return une Map contenant {id, nom, email, role}
     * @throws io.jsonwebtoken.JwtException si la signature est invalide ou le JWT expiré
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
     *
     * @return une clé HMAC adaptée à l'algorithme HS256
     * @throws IllegalStateException si SHA-256 n'est pas disponible sur la JVM
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
     *
     * @param valeurJeton la valeur UUID du jeton Bearer
     * @return l'utilisateur propriétaire du jeton
     * @throws AuthenticationFailedException si le jeton est inconnu ou expiré
     */
    // @Transactional(readOnly = true) : transaction en lecture seule (optimisation JPA, pas de flush).
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
     *
     * @param valeurJeton la valeur UUID du jeton à invalider
     */
    // @Transactional : la suppression doit être atomique.
    @Transactional
    public void deleteToken(String valeurJeton) {
        depotJetons.deleteByToken(valeurJeton);
    }
}
