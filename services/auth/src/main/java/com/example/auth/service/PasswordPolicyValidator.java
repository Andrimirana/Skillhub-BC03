package com.example.auth.service;

import com.example.auth.exception.InvalidInputException;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service de validation et d'évaluation de la force des mots de passe.
 *
 * <p>Règles de validation (TP2+) :</p>
 * <ul>
 *   <li>Longueur minimale : 12 caractères</li>
 *   <li>Au moins 1 lettre majuscule (A–Z)</li>
 *   <li>Au moins 1 lettre minuscule (a–z)</li>
 *   <li>Au moins 1 chiffre (0–9)</li>
 *   <li>Au moins 1 caractère spécial non alphanumérique</li>
 * </ul>
 *
 * <p>Les patterns regex sont pré-compilés en constantes statiques pour éviter
 * les recompilations à chaque appel et se protéger contre les attaques ReDoS.</p>

 */
@Service
public class PasswordPolicyValidator {

    private static final int    MIN_LENGTH       = 12;
    private static final Pattern HAS_UPPER       = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWER       = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT       = Pattern.compile("[0-9]");
    private static final Pattern HAS_SPECIAL     = Pattern.compile("[^a-zA-Z0-9]");

    /**
     * Valide un mot de passe selon la politique de sécurité.
     * Lève une {@link InvalidInputException} si une règle n'est pas respectée.
     *
     * @param password le mot de passe à valider
     * @throws InvalidInputException si le mot de passe est null, vide ou ne respecte pas les règles
     */
    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidInputException("Le mot de passe ne peut pas être vide");
        }
        if (password.length() < MIN_LENGTH) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères");
        }
        if (!HAS_UPPER.matcher(password).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins une lettre majuscule");
        }
        if (!HAS_LOWER.matcher(password).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins une lettre minuscule");
        }
        if (!HAS_DIGIT.matcher(password).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins un chiffre");
        }
        if (!HAS_SPECIAL.matcher(password).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins un caractrre spécial");
        }
    }

    /**
     * Évalue la force d'un mot de passe sans le stocker.
     *
     * <p>Grille d'évaluation :</p>
     * <ul>
     *   <li>{@code WEAK}   : longueur &lt; 12 ou ≤ 2 critères satisfaits</li>
     *   <li>{@code MEDIUM} : 3 critères satisfaits</li>
     *   <li>{@code STRONG} : ≥ 4 critères ET longueur ≥ 16</li>
     * </ul>
     *
     * @param password le mot de passe à évaluer
     * @return {@code "WEAK"}, {@code "MEDIUM"} ou {@code "STRONG"}
     */
    public String evaluateStrength(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "WEAK";
        }
        int score = 0;
        if (HAS_UPPER.matcher(password).find())   score++;
        if (HAS_LOWER.matcher(password).find())   score++;
        if (HAS_DIGIT.matcher(password).find())   score++;
        if (HAS_SPECIAL.matcher(password).find()) score++;

        if (score <= 2) return "WEAK";
        if (score == 3) return "MEDIUM";
        // score >= 4
        return password.length() >= 16 ? "STRONG" : "MEDIUM";
    }
}

