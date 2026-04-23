package com.example.auth.exception;

/**
 * Exception levée lors d'un conflit de ressource.
 *
 * <p>Cas d'usage : tentative d'inscription avec un email déjà existant en base.</p>
 *
 * <p>Retourne HTTP 409 Conflict via {@link GlobalExceptionHandler}.</p>
 *
 * <p> Cette implémentation est pédagogique. Ne jamais utiliser en production
 * sans audit de sécurité complet.</p>
 */
public class ResourceConflictException extends RuntimeException {

    /**
     * Crée une exception de conflit avec un message descriptif.
     *
     * @param message description du conflit
     */
    public ResourceConflictException(String message) {
        super(message);
    }
}

