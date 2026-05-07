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
 */
@Service
public class MasterKeyService {

    private static final String ALGORITHME      = "AES/GCM/NoPadding";
    private static final int    LONGUEUR_TAG    = 128;
    private static final int    LONGUEUR_IV     = 12;
    private static final String PREFIXE_FORMAT  = "v1";

    /** Instance unique et thread-safe — réutilisée à chaque chiffrement. */
    private static final SecureRandom GENERATEUR_ALEATOIRE = new SecureRandom();

    @Value("${app.master-key:}")
    private String cleMaitreBrute;

    private SecretKey cleSecrete;

    /**
     * Initialise la clé secrète au démarrage.
     * L'application refuse de démarrer si {@code APP_MASTER_KEY} est absente.
     */
    @PostConstruct
    public void init() {
        // Sans clé maître, l'application refuse de démarrer.
        if (cleMaitreBrute == null || cleMaitreBrute.isBlank()) {
            throw new IllegalStateException(
                "APP_MASTER_KEY est obligatoire. " +
                "Définissez la variable d'environnement APP_MASTER_KEY avant de démarrer l'application.");
        }
        try {
            // On dérive une clé AES-256 à partir de la clé maître via SHA-256.
            byte[] octetsCle = MessageDigest.getInstance("SHA-256")
                    .digest(cleMaitreBrute.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.cleSecrete = new SecretKeySpec(octetsCle, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Impossible d'initialiser la Master Key", e);
        }
    }

    /**
     * Chiffre un mot de passe en clair avec AES-256-GCM.
     */
    public String encrypt(String enClair) {
        try {
            // On génère un IV aléatoire de 12 octets, différent à chaque chiffrement.
            byte[] iv = new byte[LONGUEUR_IV];
            GENERATEUR_ALEATOIRE.nextBytes(iv);

            // On configure le chiffrement AES-GCM avec la clé et l'IV.
            Cipher chiffreur = Cipher.getInstance(ALGORITHME);
            chiffreur.init(Cipher.ENCRYPT_MODE, cleSecrete, new GCMParameterSpec(LONGUEUR_TAG, iv));
            // On chiffre les données en clair.
            byte[] textChiffre = chiffreur.doFinal(
                    enClair.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Format de stockage : "v1:base64(iv):base64(ciphertext)".
            return PREFIXE_FORMAT + ":"
                    + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(textChiffre);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erreur de chiffrement", e);
        }
    }

    /**
     * Déchiffre un mot de passe chiffré AES-256-GCM.
     */
    public String decrypt(String chiffre) {
        try {
            // On découpe la chaîne stockée en 3 parties.
            String[] parties = chiffre.split(":");
            // On vérifie le format : préfixe "v1" + 3 parties.
            if (parties.length != 3 || !PREFIXE_FORMAT.equals(parties[0])) {
                throw new IllegalStateException("Format de mot de passe chiffré invalide");
            }
            // On décode l'IV et le ciphertext depuis le Base64.
            byte[] iv          = Base64.getDecoder().decode(parties[1]);
            byte[] textChiffre = Base64.getDecoder().decode(parties[2]);

            // On configure le déchiffrement AES-GCM avec le même IV.
            Cipher dechiffreur = Cipher.getInstance(ALGORITHME);
            dechiffreur.init(Cipher.DECRYPT_MODE, cleSecrete, new GCMParameterSpec(LONGUEUR_TAG, iv));
            // On déchiffre. Si le tag GCM est invalide, une exception est levée.
            byte[] enClair = dechiffreur.doFinal(textChiffre);
            return new String(enClair, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Erreur générique : on évite de révéler la cause exacte au client.
            throw new IllegalStateException("Erreur de déchiffrement — ciphertext invalide ou corrompu", e);
        }
    }
}
