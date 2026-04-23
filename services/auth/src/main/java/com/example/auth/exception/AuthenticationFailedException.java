package com.example.auth.exception;

/**
 * Exception levée lorsque l'authentification échoue ou que le compte est verrouillé.
 *
 * <p>Cas d'usage :</p>
 * <ul>
 *   <li>Email inconnu en base (HTTP 401)</li>
 *   <li>Signature HMAC invalide (HTTP 401)</li>
 *   <li>Timestamp hors fenêtre ±60 secondes (HTTP 401)</li>
 *   <li>Nonce déjà utilisé — attaque par rejeu (HTTP 401)</li>
 *   <li>Token Bearer invalide ou expiré (HTTP 401)</li>
 *   <li>Compte verrouillé après 5 échecs — le message contient "bloqué" (HTTP 429)</li>
 * </ul>
 *
 * <p>Le {@link GlobalExceptionHandler} détecte le code 429 si le message
 * contient le mot "bloqué".</p>
 *
 * <p> Les messages d'erreur sont volontairement génériques pour ne pas
 * révéler si l'email existe ou non (protection contre l'énumération).</p>
 *
 * <p> Cette implémentation est pédagogique. Ne jamais utiliser en production
 * sans audit de sécurité complet.</p>
 */
public class AuthenticationFailedException extends RuntimeException {

    /**
     * Crée une exception d'authentification avec un message descriptif.
     *
     * @param message description de l'échec d'authentification
     */
    public AuthenticationFailedException(String message) {
        super(message);
    }
}

