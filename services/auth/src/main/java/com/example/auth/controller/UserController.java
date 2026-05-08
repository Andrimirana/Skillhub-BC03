package com.example.auth.controller;

import com.example.auth.entity.User;
import com.example.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur REST des endpoints utilisateur protégés.
 *
 * <p>Endpoint exposé :</p>
 * <ul>
 *   <li>{@code GET /api/me} — accessible uniquement avec un Bearer token valide</li>
 * </ul>
 *
 * <p> Cette implémentation est pédagogique. Ne jamais utiliser en production
 * sans audit de sécurité complet.</p>
 *
 * @author  Équipe SkillHub BC04
 * @version 1.0
 */
// @RestController : marque la classe comme contrôleur REST (réponses sérialisées en JSON).
@RestController
// @RequestMapping : préfixe d'URL appliqué à toutes les méthodes ci-dessous.
@RequestMapping("/api")
public class UserController {

    private final AuthService serviceAuth;

    /**
     * Constructeur — injection de dépendance par Spring.
     *
     * @param serviceAuth service métier d'authentification injecté par Spring
     */
    public UserController(AuthService serviceAuth) {
        this.serviceAuth = serviceAuth;
    }

    /**
     * Retourne les informations de l'utilisateur authentifié.
     *
     * <p>Requiert un header {@code Authorization: Bearer <token>} valide.</p>
     *
     * @param enteteAuth header HTTP {@code Authorization: Bearer <token>}
     * @return HTTP 200 avec {email, id, createdAt}
     * @throws com.example.auth.exception.AuthenticationFailedException si le token est invalide ou expiré
     */
    // @GetMapping = mappe HTTP GET /api/me vers cette méthode.
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(
            @RequestHeader("Authorization") String enteteAuth) {
        // On récupère le jeton Bearer depuis le header.
        String jeton = extraireJetonBearer(enteteAuth);
        // On retrouve l'utilisateur correspondant à ce jeton.
        User utilisateur = serviceAuth.getUserByToken(jeton);
        // On renvoie les infos publiques de l'utilisateur connecté.
        return ResponseEntity.ok(Map.of(
                "email",     utilisateur.getEmail(),
                "id",        utilisateur.getId(),
                "createdAt", utilisateur.getCreatedAt().toString()
        ));
    }

    /**
     * Extrait la valeur du jeton depuis le header Authorization.
     *
     * @param enteteAuth valeur brute du header (ex : {@code "Bearer abc123"})
     * @return le jeton seul, ou la valeur telle quelle si le préfixe est absent
     */
    private String extraireJetonBearer(String enteteAuth) {
        // On enlève le préfixe "Bearer " pour ne garder que le jeton.
        if (enteteAuth != null && enteteAuth.startsWith("Bearer ")) {
            return enteteAuth.substring(7);
        }
        return enteteAuth;
    }
}
