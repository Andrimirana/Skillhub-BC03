package com.example.auth.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Service de calcul et de vérification des signatures HMAC-SHA256.
 *
 * <p>Ce service est le cœur cryptographique du protocole d'authentification.
 * Il sert à deux choses :</p>
 * <ol>
 *   <li>Calculer {@code HMAC_SHA256(clé=motDePasse, données=email:nonce:timestamp)}</li>
 *   <li>Comparer deux signatures en <b>temps constant</b> pour empêcher
 *       les attaques temporelles (timing attacks)</li>
 * </ol>
 *
 * <p>La comparaison en temps constant via {@link MessageDigest#isEqual(byte[], byte[])}
 * est essentielle : avec {@link String#equals}, un attaquant pourrait deviner la
 * signature bit par bit en mesurant le temps de réponse.</p>
 *
 * @author  Équipe SkillHub BC04
 * @version 1.0
 */
// @Service : bean Spring de la couche service — singleton thread-safe injecté dans AuthService.
@Service
public class HmacService {

    private static final String ALGORITHME_HMAC = "HmacSHA256";

    /**
     * Calcule la signature HMAC-SHA256 d'un message.
     *
     * @param cle      la clé secrète (le mot de passe en clair de l'utilisateur)
     * @param donnees  les données à signer ({@code email:nonce:timestamp})
     * @return la signature encodée en Base64
     * @throws IllegalStateException si l'algorithme HMAC-SHA256 n'est pas disponible sur la JVM
     */
    public String compute(String cle, String donnees) {
        try {
            // On crée un calculateur HMAC-SHA256.
            Mac calculateur = Mac.getInstance(ALGORITHME_HMAC);
            // On initialise le calculateur avec la clé.
            calculateur.init(new SecretKeySpec(
                    cle.getBytes(StandardCharsets.UTF_8), ALGORITHME_HMAC));
            // On calcule la signature des données.
            byte[] resultat = calculateur.doFinal(donnees.getBytes(StandardCharsets.UTF_8));
            // On encode le résultat en Base64 pour le transport HTTP.
            return Base64.getEncoder().encodeToString(resultat);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erreur de calcul HMAC", e);
        }
    }

    /**
     * Compare deux signatures HMAC en temps constant.
     *
     * @param attendue la signature attendue (calculée côté serveur)
     * @param recue    la signature reçue du client
     * @return true si les deux signatures sont identiques
     */
    public boolean compare(String attendue, String recue) {
        // Si l'une des deux signatures est nulle, ce n'est pas valide.
        if (attendue == null || recue == null) {
            return false;
        }
        // Comparaison en temps constant pour empêcher les attaques temporelles.
        return MessageDigest.isEqual(
                attendue.getBytes(StandardCharsets.UTF_8),
                recue.getBytes(StandardCharsets.UTF_8));
    }
}
