package com.example.auth.service;

import com.example.auth.dto.ChangePasswordRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.entity.AccessToken;
import com.example.auth.entity.AuthNonce;
import com.example.auth.entity.User;
import com.example.auth.exception.AuthenticationFailedException;
import com.example.auth.exception.InvalidInputException;
import com.example.auth.exception.ResourceConflictException;
import com.example.auth.repository.AuthNonceRepository;
import com.example.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service principal d'authentification — orchestrateur de la logique métier.
 *
 * <p>Coordonne les opérations :</p>
 * <ul>
 *   <li>{@link #register} — inscription avec validation et chiffrement AES-256-GCM</li>
 *   <li>{@link #login} — protocole HMAC-SHA256 + nonce + timestamp</li>
 *   <li>{@link #changePassword} — changement de mot de passe sécurisé</li>
 *   <li>{@link #getUserByToken} — validation du jeton Bearer</li>
 *   <li>{@link #evaluatePasswordStrength} — évaluation de force sans stockage</li>
 * </ul>
 *
 * <p>Constantes de sécurité :</p>
 * <ul>
 *   <li>{@code MAX_TENTATIVES = 5} — seuil de verrouillage anti brute-force</li>
 *   <li>{@code DUREE_BLOCAGE_MIN = 2} — durée de verrouillage</li>
 *   <li>{@code FENETRE_TIMESTAMP_S = 60} — tolérance fenêtre timestamp</li>
 *   <li>{@code DUREE_VIE_NONCE_S = 120} — durée de vie d'un nonce</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger journal = LoggerFactory.getLogger(AuthService.class);

    // ── Constantes de sécurité ────────────────────────────────────────────────
    public static final int  MAX_TENTATIVES        = 5;
    public static final int  DUREE_BLOCAGE_MIN     = 2;
    public static final long FENETRE_TIMESTAMP_S   = 60L;
    public static final long DUREE_VIE_NONCE_S     = 120L;

    // Anciens noms conservés pour compatibilité avec les tests.
    public static final int  MAX_ATTEMPTS              = MAX_TENTATIVES;
    public static final int  LOCK_MINUTES              = DUREE_BLOCAGE_MIN;
    public static final long TIMESTAMP_WINDOW_SECONDS  = FENETRE_TIMESTAMP_S;
    public static final long NONCE_TTL_SECONDS         = DUREE_VIE_NONCE_S;

    private final UserRepository          depotUtilisateurs;
    private final AuthNonceRepository     depotNonces;
    private final MasterKeyService        serviceCleMaitre;
    private final HmacService             serviceHmac;
    private final TokenService            serviceJeton;
    private final PasswordPolicyValidator validateurMotDePasse;

    public AuthService(UserRepository depotUtilisateurs,
                       AuthNonceRepository depotNonces,
                       MasterKeyService serviceCleMaitre,
                       HmacService serviceHmac,
                       TokenService serviceJeton,
                       PasswordPolicyValidator validateurMotDePasse) {
        this.depotUtilisateurs    = depotUtilisateurs;
        this.depotNonces          = depotNonces;
        this.serviceCleMaitre     = serviceCleMaitre;
        this.serviceHmac          = serviceHmac;
        this.serviceJeton         = serviceJeton;
        this.validateurMotDePasse = validateurMotDePasse;
    }

    // ════════════════════════════════════════════════════════════════════
    //  INSCRIPTION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Inscrit un nouvel utilisateur.
     *
     * <p>Étapes :</p>
     * <ol>
     *   <li>Validation email (non vide, format valide)</li>
     *   <li>Validation mot de passe (politique de sécurité)</li>
     *   <li>Vérification correspondance password / passwordConfirm</li>
     *   <li>Vérification unicité email</li>
     *   <li>Chiffrement AES-256-GCM + persistance</li>
     * </ol>
     */
    @Transactional
    public Map<String, String> register(String email, String motDePasse, String confirmation) {
        // Validation email
        if (email == null || email.isBlank()) {
            throw new InvalidInputException("L'email ne peut pas être vide");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidInputException("Format d'email invalide");
        }
        // Validation mot de passe
        validateurMotDePasse.validate(motDePasse);

        // Vérification correspondance
        if (!motDePasse.equals(confirmation)) {
            throw new InvalidInputException(
                "Le mot de passe et sa confirmation ne correspondent pas");
        }
        // Unicité email
        if (depotUtilisateurs.existsByEmail(email)) {
            journal.warn("Inscription refusée — email déjà existant : {}", email);
            throw new ResourceConflictException("Cet email est déjà utilisé");
        }
        // Chiffrement + persistance
        String motDePasseChiffre = serviceCleMaitre.encrypt(motDePasse);
        depotUtilisateurs.save(new User(email, motDePasseChiffre));

        journal.info("Inscription réussie : {}", email);
        return Map.of("message", "Inscription réussie", "email", email);
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONNEXION HMAC-SHA256
    // ════════════════════════════════════════════════════════════════════

    /**
     * Authentifie un utilisateur via le protocole HMAC-SHA256.
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public LoginResponse login(LoginRequest requete) {
        // 1. Email non vide
        if (requete.email() == null || requete.email().isBlank()) {
            throw new InvalidInputException("L'email ne peut pas être vide");
        }

        // 2. Email existe
        User utilisateur = depotUtilisateurs.findByEmail(requete.email())
                .orElseThrow(() -> {
                    journal.warn("Login échoué — email inconnu : {}", requete.email());
                    return new AuthenticationFailedException("Identifiants incorrects");
                });

        // 3. Compte non verrouillé
        if (utilisateur.getLockUntil() != null && utilisateur.getLockUntil().isAfter(LocalDateTime.now())) {
            journal.warn("Login bloqué — compte verrouillé : {}", requete.email());
            throw new AuthenticationFailedException(
                "Compte bloqué — trop de tentatives. Réessayez dans " + DUREE_BLOCAGE_MIN + " minutes.");
        }

        // 4. Timestamp dans la fenêtre ±60s
        long maintenant = Instant.now().getEpochSecond();
        long ecart      = Math.abs(maintenant - requete.timestamp());
        if (ecart > FENETRE_TIMESTAMP_S) {
            incrementerTentativesEchouees(utilisateur);
            journal.warn("Login échoué — timestamp hors fenêtre : {}", requete.email());
            throw new AuthenticationFailedException("Identifiants incorrects");
        }

        // 5. Nonce non encore utilisé
        if (depotNonces.findByUserAndNonce(utilisateur, requete.nonce()).isPresent()) {
            incrementerTentativesEchouees(utilisateur);
            journal.warn("Login échoué — nonce déjà utilisé (rejeu) : {}", requete.email());
            throw new AuthenticationFailedException("Identifiants incorrects");
        }

        // 6. Enregistrement du nonce (TTL 120s)
        AuthNonce nonceAuth = new AuthNonce(
                utilisateur, requete.nonce(),
                LocalDateTime.now().plusSeconds(DUREE_VIE_NONCE_S));
        depotNonces.save(nonceAuth);

        // 7-8. Recalcul HMAC + comparaison en temps constant
        String motDePasseEnClair = serviceCleMaitre.decrypt(utilisateur.getPasswordEncrypted());
        String message           = requete.email() + ":" + requete.nonce() + ":" + requete.timestamp();
        String hmacAttendu       = serviceHmac.compute(motDePasseEnClair, message);

        if (!serviceHmac.compare(hmacAttendu, requete.hmac())) {
            incrementerTentativesEchouees(utilisateur);
            journal.warn("Login échoué — HMAC invalide : {}", requete.email());
            throw new AuthenticationFailedException("Identifiants incorrects");
        }

        // 9. Succès — réinitialisation compteur + émission jeton
        utilisateur.setFailedAttempts(0);
        utilisateur.setLockUntil(null);
        depotUtilisateurs.save(utilisateur);

        nonceAuth.setConsumed(true);
        depotNonces.save(nonceAuth);

        AccessToken jeton = serviceJeton.generate(utilisateur);
        String jwt = serviceJeton.generateJwt(utilisateur);
        journal.info("Connexion réussie : {}", requete.email());
        return new LoginResponse(jeton.getToken(), jwt, jeton.getExpiresAt());
    }

    // ════════════════════════════════════════════════════════════════════
    //  CHANGEMENT DE MOT DE PASSE
    // ════════════════════════════════════════════════════════════════════

    /**
     * Change le mot de passe d'un utilisateur authentifié.
     */
    @Transactional
    public void changePassword(String valeurJeton, ChangePasswordRequest requete) {
        // 1. Validation jeton — JWT (stateless) ou opaque UUID (lookup en base)
        User utilisateur;
        if (valeurJeton != null && valeurJeton.startsWith("eyJ")) {
            try {
                java.util.Map<String, Object> claims = serviceJeton.validateJwtClaims(valeurJeton);
                String email = (String) claims.get("email");
                utilisateur = depotUtilisateurs.findByEmail(email)
                        .orElseThrow(() -> new AuthenticationFailedException("Utilisateur introuvable"));
            } catch (io.jsonwebtoken.JwtException e) {
                throw new AuthenticationFailedException("Token invalide ou expiré");
            }
        } else {
            utilisateur = serviceJeton.getUserByToken(valeurJeton);
        }

        // 2. Vérification ancien mot de passe
        String motDePasseActuel = serviceCleMaitre.decrypt(utilisateur.getPasswordEncrypted());
        if (!motDePasseActuel.equals(requete.oldPassword())) {
            journal.warn("Changement MDP échoué ancien mot de passe incorrect : {}", utilisateur.getEmail());
            throw new AuthenticationFailedException("Ancien mot de passe incorrect");
        }

        // 3. Correspondance nouveaux mots de passe
        if (!requete.newPassword().equals(requete.confirmPassword())) {
            throw new InvalidInputException(
                "Le nouveau mot de passe et sa confirmation ne correspondent pas");
        }

        // 4. Politique de sécurité
        validateurMotDePasse.validate(requete.newPassword());

        // 5. Chiffrement + persistance
        String motDePasseChiffre = serviceCleMaitre.encrypt(requete.newPassword());
        utilisateur.setPasswordEncrypted(motDePasseChiffre);
        depotUtilisateurs.save(utilisateur);

        journal.info("Mot de passe changé avec succès : {}", utilisateur.getEmail());
    }

    // ════════════════════════════════════════════════════════════════════
    //  UTILITAIRES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Récupère l'utilisateur associé à un jeton Bearer valide.
     */
    public User getUserByToken(String valeurJeton) {
        return serviceJeton.getUserByToken(valeurJeton);
    }

    /**
     * Évalue la force d'un mot de passe sans le stocker.
     */
    public String evaluatePasswordStrength(String motDePasse) {
        return validateurMotDePasse.evaluateStrength(motDePasse);
    }

    // ════════════════════════════════════════════════════════════════════
    //  EXTENSIONS SKILLHUB
    // ════════════════════════════════════════════════════════════════════

    /**
     * Inscrit un utilisateur avec nom et rôle (Skillhub) et retourne un jeton Bearer.
     */
    @Transactional
    public AccessToken registerSkillhubUser(String email, String motDePasse,
                                            String confirmation,
                                            String nom, String role) {
        if (email == null || email.isBlank()) {
            throw new InvalidInputException("L'email ne peut pas être vide");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidInputException("Format d'email invalide");
        }
        validateurMotDePasse.validate(motDePasse);

        if (!motDePasse.equals(confirmation)) {
            throw new InvalidInputException(
                "Le mot de passe et sa confirmation ne correspondent pas");
        }
        if (depotUtilisateurs.existsByEmail(email)) {
            journal.warn("Inscription refusée — email déjà existant : {}", email);
            throw new ResourceConflictException("Cet email est déjà utilisé");
        }

        String motDePasseChiffre = serviceCleMaitre.encrypt(motDePasse);
        User utilisateur = new User(email, motDePasseChiffre);
        utilisateur.setName(nom);
        utilisateur.setRole(role != null ? role : "apprenant");
        depotUtilisateurs.save(utilisateur);

        journal.info("Inscription Skillhub réussie : {} (rôle={})", email, utilisateur.getRole());
        return serviceJeton.generate(utilisateur);
    }

    /**
     * Invalide un jeton Bearer (déconnexion Skillhub).
     */
    @Transactional
    public void logout(String valeurJeton) {
        try {
            serviceJeton.deleteToken(valeurJeton);
        } catch (Exception ignored) {
            // JWT stateless : le jeton n'est pas stocké en base — expire naturellement.
        }
        journal.info("Logout Skillhub effectué");
    }

    /**
     * Génère un JWT signé pour un utilisateur (délégation vers TokenService).
     */
    public String generateJwt(User utilisateur) {
        return serviceJeton.generateJwt(utilisateur);
    }

    /**
     * Valide un JWT et retourne les claims utilisateur sous forme de Map.
     */
    public java.util.Map<String, Object> validateJwtClaims(String jwt) {
        return serviceJeton.validateJwtClaims(jwt);
    }

    /**
     * Incrémente le compteur d'échecs et verrouille le compte si le seuil est atteint.
     */
    private void incrementerTentativesEchouees(User utilisateur) {
        utilisateur.setFailedAttempts(utilisateur.getFailedAttempts() + 1);
        if (utilisateur.getFailedAttempts() >= MAX_TENTATIVES) {
            utilisateur.setLockUntil(LocalDateTime.now().plusMinutes(DUREE_BLOCAGE_MIN));
            journal.warn("Compte verrouillé pour {} minutes : {}", DUREE_BLOCAGE_MIN, utilisateur.getEmail());
        }
        depotUtilisateurs.save(utilisateur);
    }
}
