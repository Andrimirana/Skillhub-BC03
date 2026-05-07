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
 * <p>Ce service est le cœur cryptographique du protocole d'authentification TP3+.
 * Il est responsable de deux opérations :</p>
 * <ol>
 *   <li>Calculer {@code HMAC_SHA256(key=password, data=email:nonce:timestamp)}</li>
 *   <li>Comparer deux signatures en <b>temps constant</b> pour prévenir les
 *       attaques temporelles (timing attacks)</li>
 * </ol>
 *
 * <p>La comparaison en temps constant via {@link MessageDigest#isEqual(byte[], byte[])}
 * est essentielle : avec {@link String#equals}, un attaquant pourrait mesurer le temps
 * de réponse pour deviner la signature correcte bit par bit.</p>
 */
@Service
public class HmacService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Calcule la signature HMAC-SHA256 d'un message.
     *
     * @param key  la clé secrète (mot de passe en clair de l'utilisateur)
     * @param data les données à signer ({@code email:nonce:timestamp})
     * @return la signature encodée en Base64
     * @throws IllegalStateException si le calcul échoue
     */
    public String compute(String key, String data) {
        try {
            // On crée un calculateur HMAC-SHA256.
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            // On initialise le calculateur avec le mot de passe comme clé.
            mac.init(new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            // On calcule la signature des données.
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            // On encode le résultat en Base64 pour le transport HTTP.
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erreur de calcul HMAC", e);
        }
    }

    /**
     * Compare deux signatures HMAC en temps constant.
     *
     * <p>Utilise {@link MessageDigest#isEqual(byte[], byte[])} pour éviter
     * toute fuite d'information par mesure du temps de réponse.</p>
     *
     * @param expected la signature attendue (calculée côté serveur)
     * @param received la signature reçue du client
     * @return true si les deux signatures sont identiques
     */
    public boolean compare(String expected, String received) {
        // Si l'une des deux signatures est nulle, ce n'est pas valide.
        if (expected == null || received == null) {
            return false;
        }
        // Comparaison en temps constant pour empêcher les attaques temporelles.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }
}

