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
 *
 * @author  Équipe SkillHub BC04
 * @version 1.0
 */
// @RestController = @Controller + @ResponseBody : Spring sérialise automatiquement
// les valeurs retournées en JSON et les expose comme endpoints HTTP.
@RestController
// @RequestMapping fixe le préfixe d'URL commun à tous les endpoints de la classe.
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService serviceAuth;

    /**
     * Constructeur — injection de dépendance par Spring (constructor injection).
     *
     * @param serviceAuth service métier d'authentification injecté par Spring
     */
    public AuthController(AuthService serviceAuth) {
        this.serviceAuth = serviceAuth;
    }

    /**
     * Inscrit un nouvel utilisateur dans la base.
     *
     * @param requete corps JSON contenant {email, password, passwordConfirm}
     * @return HTTP 200 avec un message de succès et l'email enregistré
     * @throws com.example.auth.exception.InvalidInputException     si l'email ou le mot de passe est invalide
     * @throws com.example.auth.exception.ResourceConflictException si l'email existe déjà
     */
    // @PostMapping mappe HTTP POST /api/auth/register vers cette méthode.
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
     *
     * @param requete corps JSON contenant {email, nonce, timestamp, hmac}
     * @return HTTP 200 avec le token UUID, le JWT signé et la date d'expiration
     * @throws com.example.auth.exception.AuthenticationFailedException si HMAC, nonce, timestamp ou compte invalides
     * @throws com.example.auth.exception.InvalidInputException         si l'email est vide
     */
    // @PostMapping("/login") : POST /api/auth/login (POST = ne pas exposer les credentials dans l'URL).
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
     *
     * @param corps JSON contenant la clé {@code password}
     * @return HTTP 200 avec {@code {"strength": "WEAK|MEDIUM|STRONG"}}
     */
    // @PostMapping : POST plutôt que GET pour éviter de logger le mot de passe dans les access logs.
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
     *
     * <p>Étapes côté service : validation du jeton, vérification de l'ancien
     * mot de passe (déchiffrement AES-GCM), application de la politique de
     * sécurité, chiffrement et persistance du nouveau mot de passe.</p>
     *
     * @param enteteAuth header HTTP {@code Authorization: Bearer <token>}
     * @param requete    corps JSON contenant {oldPassword, newPassword, confirmPassword}
     * @return HTTP 200 avec un message de confirmation
     * @throws com.example.auth.exception.AuthenticationFailedException si jeton invalide ou ancien mot de passe incorrect
     * @throws com.example.auth.exception.InvalidInputException         si le nouveau mot de passe ne respecte pas la politique
     */
    // @PutMapping = PUT /api/auth/change-password (PUT = idempotence d'une mise à jour de ressource).
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
     *
     * @param enteteAuth valeur brute du header (ex : {@code "Bearer abc123"})
     * @return le jeton seul, ou la valeur telle quelle si le préfixe est absent
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
