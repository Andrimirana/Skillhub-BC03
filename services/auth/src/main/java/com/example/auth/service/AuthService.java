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
 *   <li>{@link #changePassword} — changement de mot de passe sécurisé (TP5)</li>
 *   <li>{@link #getUserByToken} — validation Bearer token</li>
 *   <li>{@link #evaluatePasswordStrength} — évaluation de force sans stockage</li>
 * </ul>
 *
 * <p>Constantes de sécurité :</p>
 * <ul>
 *   <li>{@code MAX_ATTEMPTS = 5} — seuil de verrouillage anti brute-force</li>
 *   <li>{@code LOCK_MINUTES = 2} — durée de verrouillage</li>
 *   <li>{@code TIMESTAMP_WINDOW_SECONDS = 60} — tolérance fenêtre timestamp</li>
 *   <li>{@code NONCE_TTL_SECONDS = 120} — durée de vie d'un nonce</li>
 * </ul>
 *
 * <p> TP2 améliore le stockage mais ne protège pas encore contre le rejeu.</p>
 * <p> TP3 change le protocole — le mot de passe ne circule jamais sur le réseau.</p>
 * <p> Ce mécanisme est pédagogique. En industrie, on évite de stocker un mot de
 * passe réversible. On préférerait un hash non réversible avec protocole SRP/OPAQUE.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // ── Constantes de sécurité ────────────────────────────────────────────────
    public static final int  MAX_ATTEMPTS              = 5;
    public static final int  LOCK_MINUTES              = 2;
    public static final long TIMESTAMP_WINDOW_SECONDS  = 60L;
    public static final long NONCE_TTL_SECONDS         = 120L;

    private final UserRepository          userRepository;
    private final AuthNonceRepository     nonceRepository;
    private final MasterKeyService        masterKeyService;
    private final HmacService             hmacService;
    private final TokenService            tokenService;
    private final PasswordPolicyValidator passwordPolicy;

    public AuthService(UserRepository userRepository,
                       AuthNonceRepository nonceRepository,
                       MasterKeyService masterKeyService,
                       HmacService hmacService,
                       TokenService tokenService,
                       PasswordPolicyValidator passwordPolicy) {
        this.userRepository   = userRepository;
        this.nonceRepository  = nonceRepository;
        this.masterKeyService = masterKeyService;
        this.hmacService      = hmacService;
        this.tokenService     = tokenService;
        this.passwordPolicy   = passwordPolicy;
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
     *
     * @param email           adresse email de l'utilisateur
     * @param password        mot de passe choisi
     * @param passwordConfirm confirmation du mot de passe
     * @return map contenant {@code message} et {@code email}
     * @throws InvalidInputException     si les données sont invalides
     * @throws ResourceConflictException si l'email est déjà utilisé
     */
    @Transactional
    public Map<String, String> register(String email, String password, String passwordConfirm) {
        // Validation email
        if (email == null || email.isBlank()) {
            throw new InvalidInputException("L'email ne peut pas être vide");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidInputException("Format d'email invalide");
        }
        // Validation mot de passe
        passwordPolicy.validate(password);

        // Vérification correspondance
        if (!password.equals(passwordConfirm)) {
            throw new InvalidInputException(
                "Le mot de passe et sa confirmation ne correspondent pas");
        }
        // Unicité email
        if (userRepository.existsByEmail(email)) {
            log.warn("Inscription refusée — email déjà existant : {}", email);
            throw new ResourceConflictException("Cet email est déjà utilisé");
        }
        // Chiffrement + persistance
        String encrypted = masterKeyService.encrypt(password);
        userRepository.save(new User(email, encrypted));

        log.info("Inscription réussie : {}", email);
        return Map.of("message", "Inscription réussie", "email", email);
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONNEXION HMAC-SHA256
    // ════════════════════════════════════════════════════════════════════

    /**
     * Authentifie un utilisateur via le protocole HMAC-SHA256.
     *
     * <p>Vérifications dans cet ordre précis :</p>
     * <ol>
     *   <li>Email non vide</li>
     *   <li>Email existant en base (401 si inconnu)</li>
     *   <li>Compte non verrouillé (429 si bloqué)</li>
     *   <li>Timestamp dans la fenêtre ±60 secondes (401 si hors fenêtre)</li>
     *   <li>Nonce non encore utilisé — anti-rejeu (401 si rejoué)</li>
     *   <li>Enregistrement du nonce (TTL 120s)</li>
     *   <li>Recalcul HMAC côté serveur</li>
     *   <li>Comparaison en temps constant (401 si HMAC invalide)</li>
     *   <li>Réinitialisation compteur échecs + émission token (200)</li>
     * </ol>
     *
     * @param req la requête de login contenant email, nonce, timestamp et hmac
     * @return la réponse contenant accessToken et expiresAt
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public LoginResponse login(LoginRequest req) {
        // 1. Email non vide
        if (req.email() == null || req.email().isBlank()) {
            throw new InvalidInputException("L'email ne peut pas être vide");
        }

        // 2. Email existe
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> {
                    log.warn("Login échoué — email inconnu : {}", req.email());
                    return new AuthenticationFailedException("Identifiants incorrects");
                });

        // 3. Compte non verrouillé
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            log.warn("Login bloqué — compte verrouillé : {}", req.email());
            throw new AuthenticationFailedException(
                "Compte bloqué — trop de tentatives. Réessayez dans " + LOCK_MINUTES + " minutes.");
        }

        // 4. Timestamp dans la fenêtre ±60s
        long now  = Instant.now().getEpochSecond();
        long diff = Math.abs(now - req.timestamp());
        if (diff > TIMESTAMP_WINDOW_SECONDS) {
            incrementFailedAttempts(user);
            log.warn("Login échoué — timestamp hors fenêtre : {}", req.email());
            throw new AuthenticationFailedException("Identifiants incorrects");
        }

        // 5. Nonce non encore utilisé
        if (nonceRepository.findByUserAndNonce(user, req.nonce()).isPresent()) {
            incrementFailedAttempts(user);
            log.warn("Login échoué — nonce déjà utilisé (rejeu) : {}", req.email());
            throw new AuthenticationFailedException("Identifiants incorrects");
        }

        // 6. Enregistrement du nonce (TTL 120s)
        AuthNonce authNonce = new AuthNonce(
                user, req.nonce(),
                LocalDateTime.now().plusSeconds(NONCE_TTL_SECONDS));
        nonceRepository.save(authNonce);

        // 7-8. Recalcul HMAC + comparaison en temps constant
        String passwordPlain = masterKeyService.decrypt(user.getPasswordEncrypted());
        String message       = req.email() + ":" + req.nonce() + ":" + req.timestamp();
        String expected      = hmacService.compute(passwordPlain, message);

        if (!hmacService.compare(expected, req.hmac())) {
            incrementFailedAttempts(user);
            log.warn("Login échoué — HMAC invalide : {}", req.email());
            throw new AuthenticationFailedException("Identifiants incorrects");
        }

        // 9. Succès — réinitialisation compteur + émission token
        user.setFailedAttempts(0);
        user.setLockUntil(null);
        userRepository.save(user);

        authNonce.setConsumed(true);
        nonceRepository.save(authNonce);

        AccessToken token = tokenService.generate(user);
        log.info("Connexion réussie : {}", req.email());
        return new LoginResponse(token.getToken(), token.getExpiresAt());
    }

    // ════════════════════════════════════════════════════════════════════
    //  CHANGEMENT DE MOT DE PASSE (TP5)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Change le mot de passe d'un utilisateur authentifié.
     *
     * <p>Étapes :</p>
     * <ol>
     *   <li>Token Bearer valide → utilisateur identifié</li>
     *   <li>Déchiffrement AES-GCM + comparaison avec oldPassword</li>
     *   <li>newPassword == confirmPassword</li>
     *   <li>Politique de sécurité sur newPassword</li>
     *   <li>Chiffrement AES-256-GCM + mise à jour en base</li>
     * </ol>
     *
     * @param tokenValue le Bearer token de l'utilisateur connecté
     * @param req        la requête contenant oldPassword, newPassword, confirmPassword
     * @throws AuthenticationFailedException si le token est invalide ou l'ancien mot de passe incorrect
     * @throws InvalidInputException         si les nouveaux mots de passe ne correspondent pas ou sont trop faibles
     */
    @Transactional
    public void changePassword(String tokenValue, ChangePasswordRequest req) {
        // 1. Validation token
        User user = tokenService.getUserByToken(tokenValue);

        // 2. Vérification ancien mot de passe
        String currentPlain = masterKeyService.decrypt(user.getPasswordEncrypted());
        if (!currentPlain.equals(req.oldPassword())) {
            log.warn("Changement MDP échoué ancien mot de passe incorrect : {}", user.getEmail());
            throw new AuthenticationFailedException("Ancien mot de passe incorrect");
        }

        // Correspondance nouveaux mots de passe
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new InvalidInputException(
                "Le nouveau mot de passe et sa confirmation ne correspondent pas");
        }

        // 4. Politique de sécurité
        passwordPolicy.validate(req.newPassword());

        // 5. Chiffrement + persistance
        String encrypted = masterKeyService.encrypt(req.newPassword());
        user.setPasswordEncrypted(encrypted);
        userRepository.save(user);

        log.info("Mot de passe changé avec succès : {}", user.getEmail());
    }

    // ════════════════════════════════════════════════════════════════════
    //  UTILITAIRES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Récupère l'utilisateur associé à un Bearer token valide.
     *
     * @param tokenValue la valeur du token
     * @return l'utilisateur propriétaire du token
     */
    public User getUserByToken(String tokenValue) {
        return tokenService.getUserByToken(tokenValue);
    }

    /**
     * Évalue la force d'un mot de passe sans le stocker.
     *
     * @param password le mot de passe à évaluer
     * @return {@code "WEAK"}, {@code "MEDIUM"} ou {@code "STRONG"}
     */
    public String evaluatePasswordStrength(String password) {
        return passwordPolicy.evaluateStrength(password);
    }

    // ════════════════════════════════════════════════════════════════════
    //  EXTENSIONS SKILLHUB
    // ════════════════════════════════════════════════════════════════════

    /**
     * Inscrit un utilisateur avec nom et rôle (Skillhub) et retourne un token Bearer.
     * Réutilise toute la logique de validation d'Auth_TP1.
     *
     * @param email           adresse email
     * @param password        mot de passe
     * @param passwordConfirm confirmation du mot de passe
     * @param name            nom complet de l'utilisateur
     * @param role            rôle : formateur ou apprenant
     * @return le token d'accès généré après inscription réussie
     */
    @Transactional
    public AccessToken registerSkillhubUser(String email, String password,
                                            String passwordConfirm,
                                            String name, String role) {
        if (email == null || email.isBlank()) {
            throw new InvalidInputException("L'email ne peut pas être vide");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidInputException("Format d'email invalide");
        }
        passwordPolicy.validate(password);

        if (!password.equals(passwordConfirm)) {
            throw new InvalidInputException(
                "Le mot de passe et sa confirmation ne correspondent pas");
        }
        if (userRepository.existsByEmail(email)) {
            log.warn("Inscription refusée — email déjà existant : {}", email);
            throw new ResourceConflictException("Cet email est déjà utilisé");
        }

        String encrypted = masterKeyService.encrypt(password);
        User user = new User(email, encrypted);
        user.setName(name);
        user.setRole(role != null ? role : "apprenant");
        userRepository.save(user);

        log.info("Inscription Skillhub réussie : {} (rôle={})", email, user.getRole());
        return tokenService.generate(user);
    }

    /**
     * Invalide un token Bearer (déconnexion Skillhub).
     *
     * @param tokenValue la valeur UUID du token à supprimer
     */
    @Transactional
    public void logout(String tokenValue) {
        tokenService.deleteToken(tokenValue);
        log.info("Token invalidé (logout Skillhub)");
    }

    /**
     * Incrémente le compteur d'échecs et verrouille le compte si le seuil est atteint.
     *
     * @param user l'utilisateur en échec de connexion
     */
    private void incrementFailedAttempts(User user) {
        user.setFailedAttempts(user.getFailedAttempts() + 1);
        if (user.getFailedAttempts() >= MAX_ATTEMPTS) {
            user.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            log.warn("Compte verrouillé pour {} minutes : {}", LOCK_MINUTES, user.getEmail());
        }
        userRepository.save(user);
    }
}

