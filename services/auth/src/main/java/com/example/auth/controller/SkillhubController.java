package com.example.auth.controller;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.dto.SkillhubAuthResponse;
import com.example.auth.dto.SkillhubRegisterRequest;
import com.example.auth.dto.UtilisateurInfo;
import com.example.auth.entity.AccessToken;
import com.example.auth.entity.User;
import com.example.auth.exception.AuthenticationFailedException;
import com.example.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.Map;

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
 *   <li>{@code GET  /api/health}          — Health check Docker</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class SkillhubController {

    private final AuthService authService;

    public SkillhubController(AuthService authService) {
        this.authService = authService;
    }

    // ════════════════════════════════════════════════════════════════════
    //  INSCRIPTION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Inscrit un utilisateur et retourne immédiatement un token Bearer.
     * Accepte {nom, email, password, passwordConfirm, role}.
     */
    @PostMapping("/register")
    public ResponseEntity<SkillhubAuthResponse> register(
            @RequestBody SkillhubRegisterRequest req) {
        AccessToken token = authService.registerSkillhubUser(
                req.email(), req.password(), req.passwordConfirm(),
                req.nom(), req.role());
        return ResponseEntity.status(201)
                .body(buildAuthResponse(token));
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONNEXION — protocole HMAC-SHA256 d'Auth_TP1
    // ════════════════════════════════════════════════════════════════════

    /**
     * Authentifie via HMAC-SHA256.
     * Le frontend envoie {email, nonce, timestamp, hmac} où
     * hmac = HMAC_SHA256(clé=mot_de_passe, data="email:nonce:timestamp") en Base64.
     */
    @PostMapping("/login")
    public ResponseEntity<SkillhubAuthResponse> login(
            @RequestBody LoginRequest req) {
        LoginResponse loginResponse = authService.login(req);
        User user = authService.getUserByToken(loginResponse.accessToken());
        return ResponseEntity.ok(buildAuthResponse(loginResponse, user));
    }

    // ════════════════════════════════════════════════════════════════════
    //  PROFIL
    // ════════════════════════════════════════════════════════════════════

    /**
     * Retourne les informations de l'utilisateur authentifié.
     * Alias de GET /api/me adapté au format Skillhub.
     */
    @GetMapping("/profil")
    public ResponseEntity<Map<String, Object>> profil(
            @RequestHeader("Authorization") String authHeader) {
        User user = authService.getUserByToken(extractToken(authHeader));
        return ResponseEntity.ok(toUserMap(user));
    }

    // ════════════════════════════════════════════════════════════════════
    //  DÉCONNEXION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Invalide le token Bearer en le supprimant de la base de données.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String authHeader) {
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

        try {
            User user = authService.getUserByToken(extractToken(authHeader));
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "user",  toUserMap(user)
            ));
        } catch (AuthenticationFailedException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("valid", false, "message", "Jeton invalide ou expiré."));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  HEALTH CHECK
    // ════════════════════════════════════════════════════════════════════

    /** Endpoint de santé pour le healthcheck Docker. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
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

    private SkillhubAuthResponse buildAuthResponse(AccessToken token) {
        User user = token.getUser();
        long expiresAt = token.getExpiresAt().toEpochSecond(ZoneOffset.UTC);
        return new SkillhubAuthResponse(
                token.getToken(),
                "Bearer",
                expiresAt,
                toUtilisateurInfo(user)
        );
    }

    private SkillhubAuthResponse buildAuthResponse(LoginResponse loginResponse, User user) {
        long expiresAt = loginResponse.expiresAt().toEpochSecond(ZoneOffset.UTC);
        return new SkillhubAuthResponse(
                loginResponse.accessToken(),
                "Bearer",
                expiresAt,
                toUtilisateurInfo(user)
        );
    }

    private UtilisateurInfo toUtilisateurInfo(User user) {
        return new UtilisateurInfo(
                user.getId(),
                user.getName() != null ? user.getName() : user.getEmail(),
                user.getEmail(),
                user.getRole() != null ? user.getRole() : "apprenant"
        );
    }

    private Map<String, Object> toUserMap(User user) {
        return Map.of(
                "id",    user.getId(),
                "nom",   user.getName() != null ? user.getName() : user.getEmail(),
                "email", user.getEmail(),
                "role",  user.getRole() != null ? user.getRole() : "apprenant"
        );
    }
}
