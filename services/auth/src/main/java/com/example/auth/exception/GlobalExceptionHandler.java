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
 * @author  Équipe SkillHub BC04
 * @version 1.0
 */
// @RestControllerAdvice : intercepte les exceptions levées par tout @RestController
//                         et permet de retourner une réponse JSON normalisée.
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Gère les erreurs de validation d'entrée (HTTP 400).
     *
     * @param erreur  l'exception métier interceptée
     * @param requete la requête HTTP en cours (pour récupérer le chemin)
     * @return HTTP 400 Bad Request avec le corps JSON standardisé
     */
    // @ExceptionHandler : Spring route ici toute InvalidInputException levée par les contrôleurs.
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
     *
     * @param erreur  l'exception métier interceptée
     * @param requete la requête HTTP en cours
     * @return HTTP 401 Unauthorized ou HTTP 429 Too Many Requests
     */
    // @ExceptionHandler : Spring route ici toute AuthenticationFailedException.
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
     *
     * @param erreur  l'exception métier interceptée
     * @param requete la requête HTTP en cours
     * @return HTTP 409 Conflict avec le corps JSON standardisé
     */
    // @ExceptionHandler : Spring route ici toute ResourceConflictException.
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            ResourceConflictException erreur, HttpServletRequest requete) {
        return construireReponse(HttpStatus.CONFLICT, erreur.getMessage(), requete.getRequestURI());
    }

    /**
     * Construit la réponse JSON standardisée.
     *
     * @param statut  le statut HTTP à retourner
     * @param message le message d'erreur destiné au client
     * @param chemin  l'URI de la requête en erreur
     * @return la réponse JSON {timestamp, status, error, message, path}
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
