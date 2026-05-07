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
        // 1. Le mot de passe doit exister.
        if (password == null || password.isBlank()) {
            throw new InvalidInputException("Le mot de passe ne peut pas être vide");
        }
        // 2. Il doit faire au moins 12 caractères.
        if (password.length() < MIN_LENGTH) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères");
        }
        // 3. Il doit contenir une majuscule.
        if (!HAS_UPPER.matcher(password).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins une lettre majuscule");
        }
        // 4. Il doit contenir une minuscule.
        if (!HAS_LOWER.matcher(password).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins une lettre minuscule");
        }
        // 5. Il doit contenir un chiffre.
        if (!HAS_DIGIT.matcher(password).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins un chiffre");
        }
        // 6. Il doit contenir un caractère spécial.
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
        // Mot de passe absent ou trop court : faible.
        if (password == null || password.length() < MIN_LENGTH) {
            return "WEAK";
        }
        // On compte combien de critères sont respectés.
        int score = 0;
        if (HAS_UPPER.matcher(password).find())   score++;
        if (HAS_LOWER.matcher(password).find())   score++;
        if (HAS_DIGIT.matcher(password).find())   score++;
        if (HAS_SPECIAL.matcher(password).find()) score++;

        // Moins de 3 critères : mot de passe faible.
        if (score <= 2) return "WEAK";
        // Exactement 3 critères : niveau moyen.
        if (score == 3) return "MEDIUM";
        // 4 critères et 16+ caractères : fort, sinon moyen.
        return password.length() >= 16 ? "STRONG" : "MEDIUM";
    }
}

