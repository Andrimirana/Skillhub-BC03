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
 */
@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Retourne les informations de l'utilisateur authentifié.
     *
     * <p>Requiert un header {@code Authorization: Bearer <token>} valide.</p>
     *
     * @param authHeader header {@code Authorization: Bearer <token>}
     * @return HTTP 200 avec les informations de l'utilisateur
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractBearerToken(authHeader);
        User   user  = authService.getUserByToken(token);
        return ResponseEntity.ok(Map.of(
                "email",     user.getEmail(),
                "id",        user.getId(),
                "createdAt", user.getCreatedAt().toString()
        ));
    }

    /**
     * Extrait la valeur du token depuis le header Authorization.
     *
     * @param authHeader valeur du header {@code Authorization}
     * @return la valeur du token Bearer
     */
    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }
}

