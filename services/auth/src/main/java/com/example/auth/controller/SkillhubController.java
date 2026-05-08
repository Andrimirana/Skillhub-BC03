package com.example.auth.controller;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.dto.SkillhubAuthResponse;
import com.example.auth.dto.SkillhubRegisterRequest;
import com.example.auth.dto.UtilisateurInfo;
import com.example.auth.entity.AccessToken;
import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contrôleur REST exposant les endpoints compatibles avec le frontend Skillhub.
 *
 * <p>Ces endpoints coexistent avec les endpoints originaux d'Auth_TP1 ({@code /api/auth/*}).
 * Le protocole de connexion HMAC-SHA256 d'Auth_TP1 est conservé intégralement.</p>
 *
 * <p>Endpoints exposés :</p>
 * <ul>
 *   <li>{@code POST /api/register}        — Inscription avec nom et rôle, retourne un token</li>
 *   <li>{@code POST /api/login}           — Connexion HMAC-SHA256, retourne un token</li>
 *   <li>{@code GET  /api/profil}          — Profil de l'utilisateur connecté</li>
 *   <li>{@code POST /api/logout}          — Déconnexion (invalidation du token en base)</li>
 *   <li>{@code PUT  /api/change-password} — Changement de mot de passe</li>
 *   <li>{@code POST /api/validate-token}  — Validation interne pour catalog et inscription</li>
 *   <li>{@code GET  /api/health}          — Endpoint de santé du service</li>
 * </ul>
 *
 * @author  Équipe SkillHub BC04
 * @version 1.0
 */
// @RestController : marque la classe comme contrôleur REST (réponses sérialisées en JSON).
@RestController
// @RequestMapping : préfixe d'URL commun à tous les endpoints de la classe.
@RequestMapping("/api")
public class SkillhubController {

    private final AuthService authService;
    private final UserRepository userRepository;

    /**
     * Constructeur — injection de dépendance par Spring (constructor injection).
     *
     * @param authService    service métier d'authentification
     * @param userRepository accès JPA aux utilisateurs (utilisé par /api/users)
     */
    public SkillhubController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    // ════════════════════════════════════════════════════════════════════
    //  INSCRIPTION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Inscrit un utilisateur et retourne un JWT Bearer valide.
     * Accepte {nom, email, password, passwordConfirm, role}.
     *
     * @param req corps JSON contenant {nom, email, password, passwordConfirm, role}
     * @return HTTP 201 Created avec {token, tokenType, expiresAt, utilisateur}
     * @throws com.example.auth.exception.InvalidInputException     si l'email ou le mot de passe est invalide
     * @throws com.example.auth.exception.ResourceConflictException si l'email existe déjà
     */
    // @PostMapping : POST /api/register — création d'une nouvelle ressource utilisateur.
    @PostMapping("/register")
    public ResponseEntity<SkillhubAuthResponse> register(
            @RequestBody SkillhubRegisterRequest req) {
        // On crée le nouvel utilisateur avec son nom et son rôle.
        AccessToken token = authService.registerSkillhubUser(
                req.email(), req.password(), req.passwordConfirm(),
                req.nom(), req.role());
        // On génère un JWT signé pour le client.
        String jwt = authService.generateJwt(token.getUser());
        // On convertit la date d'expiration en timestamp Unix.
        long expiresAt = token.getExpiresAt().toEpochSecond(ZoneOffset.UTC);
        // On renvoie un 201 Created avec le token et les infos utilisateur.
        return ResponseEntity.status(201).body(
            new SkillhubAuthResponse(jwt, "Bearer", expiresAt, toUtilisateurInfo(token.getUser()))
        );
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONNEXION — protocole HMAC-SHA256 d'Auth_TP1
    // ════════════════════════════════════════════════════════════════════

    /**
     * Authentifie via HMAC-SHA256 et retourne un JWT Bearer valide.
     * Le frontend envoie {email, nonce, timestamp, hmac} où
     * hmac = HMAC_SHA256(clé=mot_de_passe, data="email:nonce:timestamp") en Base64.
     *
     * @param req corps JSON contenant {email, nonce, timestamp, hmac}
     * @return HTTP 200 avec {token JWT, tokenType, expiresAt, utilisateur}
     * @throws com.example.auth.exception.AuthenticationFailedException si HMAC, nonce, timestamp ou compte invalides
     * @throws com.example.auth.exception.InvalidInputException         si l'email est vide
     */
    // @PostMapping : POST /api/login (POST = ne pas exposer les credentials dans l'URL).
    @PostMapping("/login")
    public ResponseEntity<SkillhubAuthResponse> login(
            @RequestBody LoginRequest req) {
        // Le service Auth vérifie le HMAC et émet un token UUID + un JWT.
        LoginResponse loginResponse = authService.login(req);
        // On retrouve l'utilisateur pour enrichir la réponse.
        User user = authService.getUserByToken(loginResponse.accessToken());
        // On formate la date d'expiration en epoch Unix.
        long expiresAt = loginResponse.expiresAt().toEpochSecond(ZoneOffset.UTC);
        // On renvoie le JWT et les infos utilisateur au format Skillhub.
        return ResponseEntity.ok(new SkillhubAuthResponse(
            loginResponse.jwt(), "Bearer", expiresAt, toUtilisateurInfo(user)
        ));
    }

    // ════════════════════════════════════════════════════════════════════
    //  PROFIL
    // ════════════════════════════════════════════════════════════════════

    /**
     * Retourne les informations de l'utilisateur authentifié.
     * Accepte un JWT (frontend Skillhub) ou un UUID (endpoints legacy /api/auth/*).
     *
     * @param authHeader header HTTP {@code Authorization: Bearer <token>}
     * @return HTTP 200 avec les claims utilisateur, ou HTTP 401 si token invalide
     */
    // @GetMapping : GET /api/profil — lecture du profil sans modification d'état.
    @GetMapping("/profil")
    public ResponseEntity<Map<String, Object>> profil(
            @RequestHeader("Authorization") String authHeader) {
        // On lit le token Bearer envoyé par le client.
        String token = extractToken(authHeader);
        try {
            // Si le token commence par "eyJ", c'est un JWT : validation stateless.
            if (token != null && token.startsWith("eyJ")) {
                return ResponseEntity.ok(authService.validateJwtClaims(token));
            }
            // Sinon c'est un UUID classique : on lit l'utilisateur en base.
            User user = authService.getUserByToken(token);
            return ResponseEntity.ok(toUserMap(user));
        } catch (Exception e) {
            // Token invalide ou expiré : on retourne 401.
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé."));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  DÉCONNEXION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Déconnecte l'utilisateur. Pour les UUID : suppression en base.
     * Pour les JWT (stateless) : retourne 200 — le token expire automatiquement.
     *
     * @param authHeader header HTTP {@code Authorization: Bearer <token>}
     * @return HTTP 200 avec un message de confirmation
     */
    // @PostMapping : POST /api/logout — action qui modifie l'état serveur (suppression du token).
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String authHeader) {
        // On supprime le token de la base (cas UUID) ou on laisse expirer (cas JWT).
        authService.logout(extractToken(authHeader));
        return ResponseEntity.ok(Map.of("message", "Déconnexion effectuée."));
    }

    // ════════════════════════════════════════════════════════════════════
    //  CHANGEMENT DE MOT DE PASSE
    // ════════════════════════════════════════════════════════════════════

    /**
     * Alias de PUT /api/auth/change-password accessible depuis le frontend Skillhub.
     *
     * <p>Vérifie le token, contrôle l'ancien mot de passe (déchiffrement AES-GCM),
     * applique la politique de sécurité, puis chiffre et persiste le nouveau mot de passe.</p>
     *
     * @param authHeader header HTTP {@code Authorization: Bearer <token>}
     * @param req        corps JSON contenant {oldPassword, newPassword, confirmPassword}
     * @return HTTP 200 avec un message de confirmation
     * @throws com.example.auth.exception.AuthenticationFailedException si le token ou l'ancien mot de passe est invalide
     * @throws com.example.auth.exception.InvalidInputException         si le nouveau mot de passe ne respecte pas la politique
     */
    // @PutMapping : PUT /api/change-password — mise à jour idempotente d'une ressource.
    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody com.example.auth.dto.ChangePasswordRequest req) {
        authService.changePassword(extractToken(authHeader), req);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès."));
    }

    // ════════════════════════════════════════════════════════════════════
    //  VALIDATION INTERNE (appelée par catalog et inscription)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Valide un token Bearer et retourne les informations de l'utilisateur.
     * Accepte un JWT signé HS256 (stateless) ou un UUID opaque (lookup DB).
     * Format de réponse attendu par les middlewares PHP de Skillhub :
     * {@code {"valid": true, "user": {"id": 1, "nom": "...", "email": "...", "role": "..."}}}
     *
     * @param authHeader header HTTP {@code Authorization: Bearer <token>} (optionnel)
     * @return HTTP 200 avec {@code {valid: true, user: {...}}}, ou HTTP 401 si invalide
     */
    // @PostMapping : POST /api/validate-token — endpoint inter-services appelé par catalog/inscription.
    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(Map.of("valid", false, "message", "Jeton manquant."));
        }

        String token = extractToken(authHeader);
        try {
            if (token != null && token.startsWith("eyJ")) {
                // JWT : validation par signature (stateless — pas de requête DB)
                Map<String, Object> userClaims = authService.validateJwtClaims(token);
                return ResponseEntity.ok(Map.of("valid", true, "user", userClaims));
            }
            // UUID : lookup en base de données (endpoints legacy /api/auth/*)
            User user = authService.getUserByToken(token);
            return ResponseEntity.ok(Map.of("valid", true, "user", toUserMap(user)));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("valid", false, "message", "Jeton invalide ou expiré."));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  LISTE DES UTILISATEURS (pour le service audio)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Retourne la liste de tous les utilisateurs (id, nom, email).
     * Utilisé par le service audio pour la sélection de destinataires.
     * Nécessite un token valide.
     *
     * @param authHeader header HTTP {@code Authorization: Bearer <token>}
     * @return HTTP 200 avec {@code {utilisateurs: [...]}}, ou HTTP 401 si non autorisé
     */
    // @GetMapping : GET /api/users — lecture seule de la liste des utilisateurs.
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        try {
            if (token != null && token.startsWith("eyJ")) {
                authService.validateJwtClaims(token);
            } else {
                authService.getUserByToken(token);
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé."));
        }

        List<Map<String, Object>> utilisateurs = userRepository.findAll().stream()
                .map(u -> Map.<String, Object>of(
                        "id",    u.getId(),
                        "nom",   u.getName() != null ? u.getName() : u.getEmail(),
                        "email", u.getEmail()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("utilisateurs", utilisateurs));
    }

    // ════════════════════════════════════════════════════════════════════
    //  HEALTH CHECK
    // ════════════════════════════════════════════════════════════════════

    /**
     * Endpoint de santé du service.
     *
     * @return HTTP 200 avec {@code {"status": "UP"}}
     */
    // @GetMapping : GET /api/health — sondé par les load balancers / monitoring.
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        // Endpoint simple qui répond UP si le service répond.
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    // ════════════════════════════════════════════════════════════════════
    //  MÉTHODES PRIVÉES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Extrait la valeur du jeton depuis le header Authorization.
     *
     * @param authHeader valeur brute du header (ex : {@code "Bearer abc123"})
     * @return le jeton seul, ou la valeur telle quelle si le préfixe est absent
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }

    /**
     * Construit le DTO public {@link UtilisateurInfo} à partir d'une entité User.
     *
     * @param user l'entité utilisateur en base
     * @return DTO contenant {id, nom, email, role}
     */
    private UtilisateurInfo toUtilisateurInfo(User user) {
        // On construit le DTO public à partir de l'entité User.
        // Si le nom est vide, on utilise l'email à la place.
        // Si le rôle est vide, on met "apprenant" par défaut.
        return new UtilisateurInfo(
                user.getId(),
                user.getName() != null ? user.getName() : user.getEmail(),
                user.getEmail(),
                user.getRole() != null ? user.getRole() : "apprenant"
        );
    }

    /**
     * Variante de {@link #toUtilisateurInfo} retournant une {@code Map} (format
     * attendu par les middlewares Laravel de catalog/inscription).
     *
     * @param user l'entité utilisateur en base
     * @return une Map contenant {id, nom, email, role}
     */
    private Map<String, Object> toUserMap(User user) {
        // Même logique que toUtilisateurInfo mais sous forme de Map.
        return Map.of(
                "id",    user.getId(),
                "nom",   user.getName() != null ? user.getName() : user.getEmail(),
                "email", user.getEmail(),
                "role",  user.getRole() != null ? user.getRole() : "apprenant"
        );
    }
}
