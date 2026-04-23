package com.example.auth.exception;

/**
 * Exception levée lorsque les données fournies par le client sont invalides.
 *
 * <p>Cas d'usage : email vide ou malformé, mot de passe trop court,
 * politique de complexité non respectée, mots de passe non concordants.</p>
 *
 * <p>Retourne HTTP 400 Bad Request via {@link GlobalExceptionHandler}.</p>
 *
 * <p> Cette implémentation est pédagogique. Ne jamais utiliser en production
 * sans audit de sécurité complet.</p>
 */
public class InvalidInputException extends RuntimeException {

    /**
     * Crée une exception d'entrée invalide avec un message descriptif.
     *
     * @param message description de l'erreur de validation
     */
    public InvalidInputException(String message) {
        super(message);
    }
}

