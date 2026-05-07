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
 *
 * <p> Cette implémentation est pédagogique. Ne jamais utiliser en production
 * sans audit de sécurité complet.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Gère les erreurs de validation d'entrée (HTTP 400).
     *
     * @param ex      l'exception levée
     * @param request la requête HTTP courante
     * @return réponse JSON 400 Bad Request
     */
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidInput(
            InvalidInputException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Gère les échecs d'authentification (HTTP 401 ou 429).
     *
     * <p>Si le message contient "bloqué", retourne HTTP 429 Too Many Requests
     * pour signaler un compte verrouillé suite à trop de tentatives échouées.</p>
     *
     * @param ex      l'exception levée
     * @param request la requête HTTP courante
     * @return réponse JSON 401 ou 429
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthFailed(
            AuthenticationFailedException ex, HttpServletRequest request) {
        // Si le message indique un compte bloqué, on retourne 429 (trop de tentatives).
        // Sinon, on retourne 401 (identifiants invalides).
        HttpStatus status = (ex.getMessage() != null && ex.getMessage().contains("bloqué"))
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.UNAUTHORIZED;
        return buildResponse(status, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Gère les conflits de ressource (HTTP 409).
     *
     * @param ex      l'exception levée
     * @param request la requête HTTP courante
     * @return réponse JSON 409 Conflict
     */
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            ResourceConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Construit la réponse JSON standardisée.
     *
     * @param status  code HTTP
     * @param message message d'erreur
     * @param path    chemin de la requête
     * @return ResponseEntity avec corps JSON
     */
    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message, String path) {
        // On construit un corps JSON standardisé pour toutes les erreurs.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        body.put("path",      path);
        return ResponseEntity.status(status).body(body);
    }
}

