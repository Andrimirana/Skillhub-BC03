package com.example.auth.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service de chiffrement/déchiffrement AES-256-GCM des mots de passe.
 *
 * <p>Utilise la variable d'environnement {@code APP_MASTER_KEY} comme clé maître.
 * Si la clé est absente au démarrage, l'application refuse de démarrer.</p>
 *
 * <p>Format de stockage en base : {@code v1:Base64(iv):Base64(ciphertext)}</p>
 *
 * <p>Garanties cryptographiques :</p>
 * <ul>
 *   <li>Confidentialité + intégrité via AES-GCM (tag 128 bits)</li>
 *   <li>IV de 12 octets aléatoires via {@link SecureRandom} — différent à chaque chiffrement</li>
 *   <li>Clé dérivée via SHA-256 de la Master Key (256 bits)</li>
 * </ul>
 *
 * <p>Interdictions strictes :</p>
 * <ul>
 *   <li> Pas de clé codée en dur</li>
 *   <li> Pas d'IV fixe</li>
 *   <li> Pas de mode ECB</li>
 *   <li> Jamais loggée</li>
 * </ul>
 */
@Service
public class MasterKeyService {

    private static final String ALGORITHM      = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_LENGTH = 128;
    private static final int    GCM_IV_LENGTH  = 12;
    private static final String FORMAT_PREFIX  = "v1";

    /** Instance unique et thread-safe — réutilisée à chaque chiffrement (fix S2119). */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.master-key:}")
    private String masterKeyRaw;

    private SecretKey secretKey;

    /**
     * Initialise la clé secrète au démarrage.
     * L'application refuse de démarrer si {@code APP_MASTER_KEY} est absente.
     *
     * @throws IllegalStateException si la Master Key est manquante ou vide
     */
    @PostConstruct
    public void init() {
        if (masterKeyRaw == null || masterKeyRaw.isBlank()) {
            throw new IllegalStateException(
                "APP_MASTER_KEY est obligatoire. " +
                "Définissez la variable d'environnement APP_MASTER_KEY avant de démarrer l'application.");
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(masterKeyRaw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Impossible d'initialiser la Master Key", e);
        }
    }

    /**
     * Chiffre un mot de passe en clair avec AES-256-GCM.
     *
     * @param plaintext le mot de passe en clair
     * @return la chaîne chiffrée au format {@code v1:Base64(iv):Base64(ciphertext)}
     * @throws IllegalStateException si le chiffrement échoue
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);   // réutilisation du champ static

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return FORMAT_PREFIX + ":"
                    + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erreur de chiffrement", e);
        }
    }

    /**
     * Déchiffre un mot de passe chiffré AES-256-GCM.
     *
     * @param encrypted la chaîne chiffrée au format {@code v1:Base64(iv):Base64(ciphertext)}
     * @return le mot de passe en clair
     * @throws IllegalStateException si le déchiffrement échoue (ciphertext modifié, clé incorrecte)
     */
    public String decrypt(String encrypted) {
        try {
            String[] parts = encrypted.split(":");
            if (parts.length != 3 || !FORMAT_PREFIX.equals(parts[0])) {
                throw new IllegalStateException("Format de mot de passe chiffré invalide");
            }
            byte[] iv         = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Erreur de déchiffrement — ciphertext invalide ou corrompu", e);
        }
    }
}
