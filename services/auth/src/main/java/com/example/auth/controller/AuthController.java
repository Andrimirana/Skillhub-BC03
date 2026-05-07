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

    private final AuthService serviceAuth;

    public AuthController(AuthService serviceAuth) {
        this.serviceAuth = serviceAuth;
    }

    /**
     * Inscrit un nouvel utilisateur.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest requete) {
        // On délègue toute la logique d'inscription au service Auth.
        Map<String, String> resultat = serviceAuth.register(
                requete.email(), requete.password(), requete.passwordConfirm());
        // On renvoie un 200 OK avec le message de succès.
        return ResponseEntity.ok(resultat);
    }

    /**
     * Authentifie un utilisateur via le protocole HMAC-SHA256.
     * Le mot de passe ne circule jamais sur le réseau.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest requete) {
        // Le service Auth vérifie le HMAC, le nonce et le timestamp.
        LoginResponse reponse = serviceAuth.login(requete);
        // En cas de succès, on renvoie le JWT et la date d'expiration.
        return ResponseEntity.ok(reponse);
    }

    /**
     * Évalue la force d'un mot de passe sans le stocker.
     * POST intentionnel pour ne jamais exposer le mot de passe dans l'URL.
     */
    @PostMapping("/password-strength")
    public ResponseEntity<Map<String, String>> passwordStrength(
            @RequestBody Map<String, String> corps) {
        // On lit le mot de passe envoyé dans le corps de la requête.
        String motDePasse = corps.get("password");
        // On demande au service d'évaluer la force du mot de passe.
        String force = serviceAuth.evaluatePasswordStrength(motDePasse);
        // On renvoie le niveau au client : WEAK, MEDIUM ou STRONG.
        return ResponseEntity.ok(Map.of("strength", force));
    }

    /**
     * Permet à un utilisateur authentifié de changer son mot de passe (TP5).
     */
    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestHeader("Authorization") String enteteAuth,
            @RequestBody ChangePasswordRequest requete) {
        // On extrait le jeton du header Authorization.
        String jeton = extraireJetonBearer(enteteAuth);
        // Le service vérifie l'ancien mot de passe et applique le nouveau.
        serviceAuth.changePassword(jeton, requete);
        // On confirme au client que le changement a réussi.
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
    }

    /**
     * Extrait la valeur du jeton depuis le header Authorization.
     */
    private String extraireJetonBearer(String enteteAuth) {
        // Si le header commence par "Bearer ", on retire ce préfixe.
        if (enteteAuth != null && enteteAuth.startsWith("Bearer ")) {
            return enteteAuth.substring(7);
        }
        // Sinon on retourne la valeur telle quelle.
        return enteteAuth;
    }
}
