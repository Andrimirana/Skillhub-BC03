package com.example.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestionnaire centralisé des exceptions REST.
 *
 * <p>Intercepte toutes les exceptions métier et retourne une réponse JSON
 * standardisée avec les champs : timestamp, status, error, message, path.</p>
 *
 * <p>Format de réponse :</p>
 * <pre>
 * {
 *   "timestamp": "2026-03-25T00:30:00",
 *   "status":    401,
 *   "error":     "Unauthorized",
 *   "message":   "Identifiants incorrects",
 *   "path":      "/api/auth/login"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Gère les erreurs de validation d'entrée (HTTP 400).
     */
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidInput(
            InvalidInputException erreur, HttpServletRequest requete) {
        return construireReponse(HttpStatus.BAD_REQUEST, erreur.getMessage(), requete.getRequestURI());
    }

    /**
     * Gère les échecs d'authentification (HTTP 401 ou 429).
     *
     * <p>Si le message contient "bloqué", retourne HTTP 429 Too Many Requests
     * pour signaler un compte verrouillé suite à trop de tentatives échouées.</p>
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthFailed(
            AuthenticationFailedException erreur, HttpServletRequest requete) {
        // Si le message indique un compte bloqué, on retourne 429 (trop de tentatives).
        // Sinon, on retourne 401 (identifiants invalides).
        HttpStatus statut = (erreur.getMessage() != null && erreur.getMessage().contains("bloqué"))
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.UNAUTHORIZED;
        return construireReponse(statut, erreur.getMessage(), requete.getRequestURI());
    }

    /**
     * Gère les conflits de ressource (HTTP 409).
     */
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            ResourceConflictException erreur, HttpServletRequest requete) {
        return construireReponse(HttpStatus.CONFLICT, erreur.getMessage(), requete.getRequestURI());
    }

    /**
     * Construit la réponse JSON standardisée.
     */
    private ResponseEntity<Map<String, Object>> construireReponse(
            HttpStatus statut, String message, String chemin) {
        // On construit un corps JSON standardisé pour toutes les erreurs.
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("timestamp", LocalDateTime.now().toString());
        corps.put("status",    statut.value());
        corps.put("error",     statut.getReasonPhrase());
        corps.put("message",   message);
        corps.put("path",      chemin);
        return ResponseEntity.status(statut).body(corps);
    }
}
