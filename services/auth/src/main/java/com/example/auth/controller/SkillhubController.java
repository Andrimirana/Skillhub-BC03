package com.example.auth.controller;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.dto.SkillhubAuthResponse;
import com.example.auth.dto.SkillhubRegisterRequest;
import com.example.auth.dto.UtilisateurInfo;
import com.example.auth.entity.AccessToken;
import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contrôleur REST exposant les endpoints compatibles avec le frontend Skillhub.
 *
 * <p>Ces endpoints coexistent avec les endpoints originaux d'Auth_TP1 ({@code /api/auth/*}).
 * Le protocole de connexion HMAC-SHA256 d'Auth_TP1 est conservé intégralement.</p>
 *
 * <p>Endpoints exposés :</p>
 * <ul>
 *   <li>{@code POST /api/register}        — Inscription avec nom et rôle, retourne un token</li>
 *   <li>{@code POST /api/login}           — Connexion HMAC-SHA256, retourne un token</li>
 *   <li>{@code GET  /api/profil}          — Profil de l'utilisateur connecté</li>
 *   <li>{@code POST /api/logout}          — Déconnexion (invalidation du token en base)</li>
 *   <li>{@code PUT  /api/change-password} — Changement de mot de passe</li>
 *   <li>{@code POST /api/validate-token}  — Validation interne pour catalog et inscription</li>
 *   <li>{@code GET  /api/health}          — Endpoint de santé du service</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class SkillhubController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public SkillhubController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    // ════════════════════════════════════════════════════════════════════
    //  INSCRIPTION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Inscrit un utilisateur et retourne un JWT Bearer valide.
     * Accepte {nom, email, password, passwordConfirm, role}.
     */
    @PostMapping("/register")
    public ResponseEntity<SkillhubAuthResponse> register(
            @RequestBody SkillhubRegisterRequest req) {
        // On crée le nouvel utilisateur avec son nom et son rôle.
        AccessToken token = authService.registerSkillhubUser(
                req.email(), req.password(), req.passwordConfirm(),
                req.nom(), req.role());
        // On génère un JWT signé pour le client.
        String jwt = authService.generateJwt(token.getUser());
        // On convertit la date d'expiration en timestamp Unix.
        long expiresAt = token.getExpiresAt().toEpochSecond(ZoneOffset.UTC);
        // On renvoie un 201 Created avec le token et les infos utilisateur.
        return ResponseEntity.status(201).body(
            new SkillhubAuthResponse(jwt, "Bearer", expiresAt, toUtilisateurInfo(token.getUser()))
        );
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONNEXION — protocole HMAC-SHA256 d'Auth_TP1
    // ════════════════════════════════════════════════════════════════════

    /**
     * Authentifie via HMAC-SHA256 et retourne un JWT Bearer valide.
     * Le frontend envoie {email, nonce, timestamp, hmac} où
     * hmac = HMAC_SHA256(clé=mot_de_passe, data="email:nonce:timestamp") en Base64.
     */
    @PostMapping("/login")
    public ResponseEntity<SkillhubAuthResponse> login(
            @RequestBody LoginRequest req) {
        // Le service Auth vérifie le HMAC et émet un token UUID + un JWT.
        LoginResponse loginResponse = authService.login(req);
        // On retrouve l'utilisateur pour enrichir la réponse.
        User user = authService.getUserByToken(loginResponse.accessToken());
        // On formate la date d'expiration en epoch Unix.
        long expiresAt = loginResponse.expiresAt().toEpochSecond(ZoneOffset.UTC);
        // On renvoie le JWT et les infos utilisateur au format Skillhub.
        return ResponseEntity.ok(new SkillhubAuthResponse(
            loginResponse.jwt(), "Bearer", expiresAt, toUtilisateurInfo(user)
        ));
    }

    // ════════════════════════════════════════════════════════════════════
    //  PROFIL
    // ════════════════════════════════════════════════════════════════════

    /**
     * Retourne les informations de l'utilisateur authentifié.
     * Accepte un JWT (frontend Skillhub) ou un UUID (endpoints legacy /api/auth/*).
     */
    @GetMapping("/profil")
    public ResponseEntity<Map<String, Object>> profil(
            @RequestHeader("Authorization") String authHeader) {
        // On lit le token Bearer envoyé par le client.
        String token = extractToken(authHeader);
        try {
            // Si le token commence par "eyJ", c'est un JWT : validation stateless.
            if (token != null && token.startsWith("eyJ")) {
                return ResponseEntity.ok(authService.validateJwtClaims(token));
            }
            // Sinon c'est un UUID classique : on lit l'utilisateur en base.
            User user = authService.getUserByToken(token);
            return ResponseEntity.ok(toUserMap(user));
        } catch (Exception e) {
            // Token invalide ou expiré : on retourne 401.
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé."));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  DÉCONNEXION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Déconnecte l'utilisateur. Pour les UUID : suppression en base.
     * Pour les JWT (stateless) : retourne 200 — le token expire automatiquement.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String authHeader) {
        // On supprime le token de la base (cas UUID) ou on laisse expirer (cas JWT).
        authService.logout(extractToken(authHeader));
        return ResponseEntity.ok(Map.of("message", "Déconnexion effectuée."));
    }

    // ════════════════════════════════════════════════════════════════════
    //  CHANGEMENT DE MOT DE PASSE
    // ════════════════════════════════════════════════════════════════════

    /**
     * Alias de PUT /api/auth/change-password accessible depuis le frontend Skillhub.
     */
    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody com.example.auth.dto.ChangePasswordRequest req) {
        authService.changePassword(extractToken(authHeader), req);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès."));
    }

    // ════════════════════════════════════════════════════════════════════
    //  VALIDATION INTERNE (appelée par catalog et inscription)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Valide un token Bearer et retourne les informations de l'utilisateur.
     * Accepte un JWT signé HS256 (stateless) ou un UUID opaque (lookup DB).
     * Format de réponse attendu par les middlewares PHP de Skillhub :
     * {@code {"valid": true, "user": {"id": 1, "nom": "...", "email": "...", "role": "..."}}}
     */
    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(Map.of("valid", false, "message", "Jeton manquant."));
        }

        String token = extractToken(authHeader);
        try {
            if (token != null && token.startsWith("eyJ")) {
                // JWT : validation par signature (stateless — pas de requête DB)
                Map<String, Object> userClaims = authService.validateJwtClaims(token);
                return ResponseEntity.ok(Map.of("valid", true, "user", userClaims));
            }
            // UUID : lookup en base de données (endpoints legacy /api/auth/*)
            User user = authService.getUserByToken(token);
            return ResponseEntity.ok(Map.of("valid", true, "user", toUserMap(user)));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("valid", false, "message", "Jeton invalide ou expiré."));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  LISTE DES UTILISATEURS (pour le service audio)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Retourne la liste de tous les utilisateurs (id, nom, email).
     * Utilisé par le service audio pour la sélection de destinataires.
     * Nécessite un token valide.
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        try {
            if (token != null && token.startsWith("eyJ")) {
                authService.validateJwtClaims(token);
            } else {
                authService.getUserByToken(token);
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé."));
        }

        List<Map<String, Object>> utilisateurs = userRepository.findAll().stream()
                .map(u -> Map.<String, Object>of(
                        "id",    u.getId(),
                        "nom",   u.getName() != null ? u.getName() : u.getEmail(),
                        "email", u.getEmail()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("utilisateurs", utilisateurs));
    }

    // ════════════════════════════════════════════════════════════════════
    //  HEALTH CHECK
    // ════════════════════════════════════════════════════════════════════

    /** Endpoint de santé du service. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        // Endpoint simple qui répond UP si le service répond.
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    // ════════════════════════════════════════════════════════════════════
    //  MÉTHODES PRIVÉES
    // ════════════════════════════════════════════════════════════════════

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }

    private UtilisateurInfo toUtilisateurInfo(User user) {
        // On construit le DTO public à partir de l'entité User.
        // Si le nom est vide, on utilise l'email à la place.
        // Si le rôle est vide, on met "apprenant" par défaut.
        return new UtilisateurInfo(
                user.getId(),
                user.getName() != null ? user.getName() : user.getEmail(),
                user.getEmail(),
                user.getRole() != null ? user.getRole() : "apprenant"
        );
    }

    private Map<String, Object> toUserMap(User user) {
        // Même logique que toUtilisateurInfo mais sous forme de Map.
        return Map.of(
                "id",    user.getId(),
                "nom",   user.getName() != null ? user.getName() : user.getEmail(),
                "email", user.getEmail(),
                "role",  user.getRole() != null ? user.getRole() : "apprenant"
        );
    }
}
