package com.example.auth.controller;

import com.example.auth.dto.ChangePasswordRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur REST des endpoints d'authentification.
 *
 * <p>Endpoints exposés :</p>
 * <ul>
 *   <li>{@code POST /api/auth/register}         — Inscription</li>
 *   <li>{@code POST /api/auth/login}             — Connexion HMAC-SHA256</li>
 *   <li>{@code POST /api/auth/password-strength} — Évaluation force mot de passe</li>
 *   <li>{@code PUT  /api/auth/change-password}   — Changement de mot de passe (TP5)</li>
 * </ul>
 *
 * <p> Cette implémentation est pédagogique. Ne jamais utiliser en production
 * sans audit de sécurité complet.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Inscrit un nouvel utilisateur.
     *
     * @param req corps de la requête : email, password, passwordConfirm
     * @return HTTP 200 avec message de succès et email
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest req) {
        // On délègue toute la logique d'inscription au service Auth.
        Map<String, String> result = authService.register(
                req.email(), req.password(), req.passwordConfirm());
        // On renvoie un 200 OK avec le message de succès.
        return ResponseEntity.ok(result);
    }

    /**
     * Authentifie un utilisateur via le protocole HMAC-SHA256.
     * Le mot de passe ne circule jamais sur le réseau.
     *
     * @param req corps de la requête : email, nonce, timestamp, hmac
     * @return HTTP 200 avec accessToken et expiresAt
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        // Le service Auth vérifie le HMAC, le nonce et le timestamp.
        LoginResponse response = authService.login(req);
        // En cas de succès, on renvoie le JWT et la date d'expiration.
        return ResponseEntity.ok(response);
    }

    /**
     * Évalue la force d'un mot de passe sans le stocker.
     * POST intentionnel pour ne jamais exposer le mot de passe dans l'URL.
     *
     * @param body map contenant la clé {@code password}
     * @return HTTP 200 avec {@code {"strength": "WEAK"|"MEDIUM"|"STRONG"}}
     */
    @PostMapping("/password-strength")
    public ResponseEntity<Map<String, String>> passwordStrength(
            @RequestBody Map<String, String> body) {
        // On lit le mot de passe envoyé dans le corps de la requête.
        String password = body.get("password");
        // On demande au service d'évaluer la force du mot de passe.
        String strength = authService.evaluatePasswordStrength(password);
        // On renvoie le niveau au client : WEAK, MEDIUM ou STRONG.
        return ResponseEntity.ok(Map.of("strength", strength));
    }

    /**
     * Permet à un utilisateur authentifié de changer son mot de passe (TP5).
     *
     * @param authHeader header {@code Authorization: Bearer <token>}
     * @param req        corps de la requête : oldPassword, newPassword, confirmPassword
     * @return HTTP 200 avec message de succès
     */
    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ChangePasswordRequest req) {
        // On extrait le token du header Authorization.
        String token = extractBearerToken(authHeader);
        // Le service vérifie l'ancien mot de passe et applique le nouveau.
        authService.changePassword(token, req);
        // On confirme au client que le changement a réussi.
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
    }

    /**
     * Extrait la valeur du token depuis le header Authorization.
     *
     * @param authHeader valeur du header {@code Authorization}
     * @return la valeur du token Bearer
     */
    private String extractBearerToken(String authHeader) {
        // Si le header commence par "Bearer ", on retire ce préfixe.
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // Sinon on retourne la valeur telle quelle.
        return authHeader;
    }
}

