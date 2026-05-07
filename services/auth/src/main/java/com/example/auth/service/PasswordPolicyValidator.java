package com.example.auth.service;

import com.example.auth.exception.InvalidInputException;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service de validation et d'évaluation de la force des mots de passe.
 *
 * <p>Règles de validation :</p>
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

    private static final int     LONGUEUR_MIN  = 12;
    private static final Pattern A_MAJUSCULE   = Pattern.compile("[A-Z]");
    private static final Pattern A_MINUSCULE   = Pattern.compile("[a-z]");
    private static final Pattern A_CHIFFRE     = Pattern.compile("[0-9]");
    private static final Pattern A_SPECIAL     = Pattern.compile("[^a-zA-Z0-9]");

    /**
     * Valide un mot de passe selon la politique de sécurité.
     * Lève une {@link InvalidInputException} si une règle n'est pas respectée.
     */
    public void validate(String motDePasse) {
        // 1. Le mot de passe doit exister.
        if (motDePasse == null || motDePasse.isBlank()) {
            throw new InvalidInputException("Le mot de passe ne peut pas être vide");
        }
        // 2. Il doit faire au moins 12 caractères.
        if (motDePasse.length() < LONGUEUR_MIN) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins " + LONGUEUR_MIN + " caractères");
        }
        // 3. Il doit contenir une majuscule.
        if (!A_MAJUSCULE.matcher(motDePasse).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins une lettre majuscule");
        }
        // 4. Il doit contenir une minuscule.
        if (!A_MINUSCULE.matcher(motDePasse).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins une lettre minuscule");
        }
        // 5. Il doit contenir un chiffre.
        if (!A_CHIFFRE.matcher(motDePasse).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins un chiffre");
        }
        // 6. Il doit contenir un caractère spécial.
        if (!A_SPECIAL.matcher(motDePasse).find()) {
            throw new InvalidInputException(
                "Le mot de passe doit contenir au moins un caractère spécial");
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
     */
    public String evaluateStrength(String motDePasse) {
        // Mot de passe absent ou trop court : faible.
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN) {
            return "WEAK";
        }
        // On compte combien de critères sont respectés.
        int score = 0;
        if (A_MAJUSCULE.matcher(motDePasse).find()) score++;
        if (A_MINUSCULE.matcher(motDePasse).find()) score++;
        if (A_CHIFFRE.matcher(motDePasse).find())   score++;
        if (A_SPECIAL.matcher(motDePasse).find())   score++;

        // Moins de 3 critères : mot de passe faible.
        if (score <= 2) return "WEAK";
        // Exactement 3 critères : niveau moyen.
        if (score == 3) return "MEDIUM";
        // 4 critères et 16+ caractères : fort, sinon moyen.
        return motDePasse.length() >= 16 ? "STRONG" : "MEDIUM";
    }
}
