package com.example.auth;

import com.example.auth.dto.ChangePasswordRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.entity.AccessToken;
import com.example.auth.entity.User;
import com.example.auth.exception.AuthenticationFailedException;
import com.example.auth.exception.InvalidInputException;
import com.example.auth.exception.ResourceConflictException;
import com.example.auth.repository.AccessTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests d'intégration — Serveur d'Authentification HMAC-SHA256 (TP1 → TP5).
 *
 * <p>Utilise H2 en mémoire — aucune dépendance MySQL requise en CI/CD.</p>
 * <p>Couverture minimum : 80 % (objectif SonarCloud TP3+).</p>
 *
 * <p> Cette implémentation est pédagogique. Ne jamais utiliser en production
 * sans audit de sécurité complet.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApplicationTests {

    @Autowired private MockMvc              mockMvc;
    @Autowired private AuthService          authService;
    @Autowired private HmacService          hmacService;
    @Autowired private MasterKeyService     masterKeyService;
    @Autowired private PasswordPolicyValidator passwordPolicy;
    @Autowired private UserRepository       userRepository;
    @Autowired private AccessTokenRepository tokenRepository;
    @Autowired private com.example.auth.repository.AuthNonceRepository nonceRepository;

    private static final String EMAIL    = "test@example.com";
    private static final String PASSWORD = "TestPassword1!";

    @BeforeEach
    void setUp() {
        // Respecter l'ordre des FK : tokens et nonces avant users
        tokenRepository.deleteAll();
        nonceRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ════════════════════════════════════════════════════════════════
    //  TP1 — Validation email
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T01 — Inscription KO si email vide → 400")
    void registerEmailVide() {
        assertThatThrownBy(() -> authService.register("", PASSWORD, PASSWORD))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("T02 — Inscription KO si email format invalide → 400")
    void registerEmailFormatInvalide() {
        assertThatThrownBy(() -> authService.register("pasunemail", PASSWORD, PASSWORD))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("T03 — Inscription KO si mot de passe trop court → 400")
    void registerMotDePasseTropCourt() {
        assertThatThrownBy(() -> authService.register(EMAIL, "Ab1!", "Ab1!"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("12");
    }

    @Test
    @DisplayName("T04 — Inscription OK → 200")
    void registerOk() {
        var result = authService.register(EMAIL, PASSWORD, PASSWORD);
        assertThat(result).containsEntry("message", "Inscription réussie");
        assertThat(result).containsEntry("email", EMAIL);
        assertThat(userRepository.existsByEmail(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("T05 — Inscription refusée si email déjà existant → 409")
    void registerEmailDejaExistant() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        assertThatThrownBy(() -> authService.register(EMAIL, PASSWORD, PASSWORD))
                .isInstanceOf(ResourceConflictException.class);
    }

    // ════════════════════════════════════════════════════════════════
    //  TP3 — Login HMAC
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T06 — Login OK avec HMAC valide → token retourné")
    void loginHmacValide() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        LoginResponse resp = doValidLogin(EMAIL, PASSWORD);
        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.expiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("T07 — Login KO si mot de passe incorrect (HMAC invalide) → 401")
    void loginMotDePasseIncorrect() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        assertThatThrownBy(() -> doValidLogin(EMAIL, "MauvaisPassword1!"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("T08 — Login KO si email inconnu → 401")
    void loginEmailInconnu() {
        String nonce     = UUID.randomUUID().toString();
        long   timestamp = Instant.now().getEpochSecond();
        String message   = "inconnu@example.com:" + nonce + ":" + timestamp;
        String hmac      = hmacService.compute(PASSWORD, message);
        var req = new LoginRequest("inconnu@example.com", nonce, timestamp, hmac);
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("T09 — Login KO si timestamp expiré (> 60s) → 401")
    void loginTimestampExpire() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        long   oldTs  = Instant.now().getEpochSecond() - 120; // 120s dans le passé
        String nonce   = UUID.randomUUID().toString();
        String message = EMAIL + ":" + nonce + ":" + oldTs;
        String hmac    = hmacService.compute(PASSWORD, message);
        var req = new LoginRequest(EMAIL, nonce, oldTs, hmac);
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("T10 — Login KO si timestamp futur (> 60s) → 401")
    void loginTimestampFutur() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        long   futureTs = Instant.now().getEpochSecond() + 120;
        String nonce    = UUID.randomUUID().toString();
        String message  = EMAIL + ":" + nonce + ":" + futureTs;
        String hmac     = hmacService.compute(PASSWORD, message);
        var req = new LoginRequest(EMAIL, nonce, futureTs, hmac);
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("T11 — Login KO si nonce déjà utilisé (anti-rejeu) → 401")
    void loginNonceDejaUtilise() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        long   timestamp = Instant.now().getEpochSecond();
        String nonce     = UUID.randomUUID().toString();
        String message   = EMAIL + ":" + nonce + ":" + timestamp;
        String hmac      = hmacService.compute(PASSWORD, message);
        var req = new LoginRequest(EMAIL, nonce, timestamp, hmac);
        authService.login(req); // Premier login OK
        assertThatThrownBy(() -> authService.login(req)) // Rejeu → 401
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("T12 — Comparaison HMAC en temps constant (pas de fuite timing)")
    void hmacComparaisonTempsConstant() {
        // MessageDigest.isEqual doit retourner false sans fuite d'information
        assertThat(hmacService.compare("abc", "xyz")).isFalse();
        assertThat(hmacService.compare("abc", "abc")).isTrue();
        assertThat(hmacService.compare(null,  "xyz")).isFalse();
        assertThat(hmacService.compare("abc", null)).isFalse();
    }

    @Test
    @DisplayName("T13 — Token émis → accès /api/me OK → 200")
    void tokenEmisPuisAccesMeOk() throws Exception {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        LoginResponse resp = doValidLogin(EMAIL, PASSWORD);
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + resp.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("T14 — Accès /api/me sans token → 401")
    void accesMeSansToken() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    // ════════════════════════════════════════════════════════════════
    //  TP2 — Anti brute-force
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T15 — Compte verrouillé après 5 échecs → 429")
    void compteLockoutApres5Echecs() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        // 5 tentatives échouées
        for (int i = 0; i < 5; i++) {
            try { doValidLogin(EMAIL, "MauvaisPassword1!"); } catch (Exception ignored) { /* expected */ }
        }
        // La 6e doit déclencher le lockout
        assertThatThrownBy(() -> doValidLogin(EMAIL, PASSWORD))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("bloqué");
    }

    @Test
    @DisplayName("T16 — Non-divulgation : même message pour email inconnu ET mauvais MDP")
    void nonDivulgationErreurMessage() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        // Email inconnu
        String nonce = UUID.randomUUID().toString();
        long ts = Instant.now().getEpochSecond();
        var req1 = new LoginRequest("inconnu@example.com", nonce, ts,
                hmacService.compute(PASSWORD, "inconnu@example.com:" + nonce + ":" + ts));
        // Mauvais MDP
        String nonce2 = UUID.randomUUID().toString();
        var req2 = new LoginRequest(EMAIL, nonce2, ts,
                hmacService.compute("MauvaisMdp1!", EMAIL + ":" + nonce2 + ":" + ts));

        String msg1 = null, msg2 = null;
        try { authService.login(req1); } catch (AuthenticationFailedException e) { msg1 = e.getMessage(); }
        try { authService.login(req2); } catch (AuthenticationFailedException e) { msg2 = e.getMessage(); }
        assertThat(msg1).isEqualTo(msg2);
    }

    // ════════════════════════════════════════════════════════════════
    //  TP4 — Master Key AES-256-GCM
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T17 — Chiffrement/Déchiffrement OK — texte récupéré intact")
    void masterKeyEncryptDecryptOk() {
        String plain     = "MonMotDePasse123!";
        String encrypted = masterKeyService.encrypt(plain);
        String decrypted = masterKeyService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    @DisplayName("T18 — Mot de passe chiffré ≠ mot de passe clair")
    void masterKeyChiffreEstDifferentDuClair() {
        String plain     = "MonMotDePasse123!";
        String encrypted = masterKeyService.encrypt(plain);
        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(encrypted).startsWith("v1:");
    }

    @Test
    @DisplayName("T19 — Déchiffrement KO si ciphertext modifié (intégrité GCM)")
    void masterKeyDecryptEchoueAvecCiphertextModifie() {
        String encrypted = masterKeyService.encrypt("MonMotDePasse123!");
        String tampered  = encrypted + "XXXXXX";
        assertThatThrownBy(() -> masterKeyService.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("T20 — Login OK avec mot de passe chiffré en base")
    void loginOkAvecMotDePasseChiffreEnBase() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        // Vérifier que le mot de passe est bien chiffré en base
        assertThat(user.getPasswordEncrypted()).startsWith("v1:");
        // Et que le login fonctionne
        LoginResponse resp = doValidLogin(EMAIL, PASSWORD);
        assertThat(resp.accessToken()).isNotBlank();
    }

    // ════════════════════════════════════════════════════════════════
    //  TP5 — Changement de mot de passe
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T21 — Changement de mot de passe réussi → 200")
    void changePasswordOk() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        LoginResponse login = doValidLogin(EMAIL, PASSWORD);
        String newPwd = "NouveauPassword2@";
        authService.changePassword(login.accessToken(),
                new ChangePasswordRequest(PASSWORD, newPwd, newPwd));
        // Vérifier que le nouveau mot de passe fonctionne
        LoginResponse newLogin = doValidLogin(EMAIL, newPwd);
        assertThat(newLogin.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("T22 — Changement KO si ancien mot de passe incorrect → 401")
    void changePasswordAncienIncorrect() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        LoginResponse login = doValidLogin(EMAIL, PASSWORD);
        assertThatThrownBy(() -> authService.changePassword(login.accessToken(),
                new ChangePasswordRequest("MauvaisAncien1!", "NouveauPassword2@", "NouveauPassword2@")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("T23 — Changement KO si confirmPassword différent → 400")
    void changePasswordConfirmDifferent() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        LoginResponse login = doValidLogin(EMAIL, PASSWORD);
        assertThatThrownBy(() -> authService.changePassword(login.accessToken(),
                new ChangePasswordRequest(PASSWORD, "NouveauPassword2@", "AutrePassword3#")))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("T24 — Changement KO si nouveau mot de passe trop faible → 400")
    void changePasswordNouveauTropFaible() {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        LoginResponse login = doValidLogin(EMAIL, PASSWORD);
        assertThatThrownBy(() -> authService.changePassword(login.accessToken(),
                new ChangePasswordRequest(PASSWORD, "faible", "faible")))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("T25 — Changement KO si token invalide → 401")
    void changePasswordTokenInvalide() {
        assertThatThrownBy(() -> authService.changePassword("token-invalide",
                new ChangePasswordRequest(PASSWORD, "NouveauPassword2@", "NouveauPassword2@")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("T26 — Endpoint PUT /api/auth/change-password → 200")
    void changePasswordEndpoint() throws Exception {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        LoginResponse login = doValidLogin(EMAIL, PASSWORD);
        String json = """
            {"oldPassword":"TestPassword1!",
             "newPassword":"NouveauPassword2@",
             "confirmPassword":"NouveauPassword2@"}
            """;
        mockMvc.perform(put("/api/auth/change-password")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mot de passe modifié avec succès"));
    }

    // ════════════════════════════════════════════════════════════════
    //  Compte de test obligatoire
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T27 — Compte de test toto@example.com / TestPassword1! fonctionne")
    void compteDeTestObligatoire() {
        authService.register("toto@example.com", "TestPassword1!", "TestPassword1!");
        LoginResponse resp = doValidLogin("toto@example.com", "TestPassword1!");
        assertThat(resp.accessToken()).isNotBlank();
    }

    // ════════════════════════════════════════════════════════════════
    //  HTTP Controller — endpoints non couverts par les tests service
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T30 — POST /api/auth/register → 200")
    void httpRegisterOk() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"http@example.com\","
                               + "\"password\":\"TestPassword1!\","
                               + "\"passwordConfirm\":\"TestPassword1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Inscription réussie"))
                .andExpect(jsonPath("$.email").value("http@example.com"));
    }

    @Test
    @DisplayName("T31 — POST /api/auth/register email vide → 400")
    void httpRegisterEmailVide() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\","
                               + "\"password\":\"TestPassword1!\","
                               + "\"passwordConfirm\":\"TestPassword1!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T32 — POST /api/auth/register email déjà existant → 409")
    void httpRegisterEmailDejaExistant() throws Exception {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\","
                               + "\"password\":\"TestPassword1!\","
                               + "\"passwordConfirm\":\"TestPassword1!\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("T33 — POST /api/auth/login OK → 200 avec token")
    void httpLoginOk() throws Exception {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        String nonce     = UUID.randomUUID().toString();
        long   timestamp = Instant.now().getEpochSecond();
        String message   = EMAIL + ":" + nonce + ":" + timestamp;
        String hmac      = hmacService.compute(PASSWORD, message);
        String json = String.format(
            "{\"email\":\"%s\",\"nonce\":\"%s\",\"timestamp\":%d,\"hmac\":\"%s\"}",
            EMAIL, nonce, timestamp, hmac);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("T34 — POST /api/auth/login HMAC invalide → 401")
    void httpLoginHmacInvalide() throws Exception {
        authService.register(EMAIL, PASSWORD, PASSWORD);
        String nonce     = UUID.randomUUID().toString();
        long   timestamp = Instant.now().getEpochSecond();
        String json = String.format(
            "{\"email\":\"%s\",\"nonce\":\"%s\",\"timestamp\":%d,\"hmac\":\"invalide\"}",
            EMAIL, nonce, timestamp);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T35 — POST /api/auth/password-strength → STRONG")
    void httpPasswordStrengthStrong() throws Exception {
        mockMvc.perform(post("/api/auth/password-strength")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"SuperStr0ng!Password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strength").value("STRONG"));
    }

    @Test
    @DisplayName("T36 — POST /api/auth/password-strength → WEAK")
    void httpPasswordStrengthWeak() throws Exception {
        mockMvc.perform(post("/api/auth/password-strength")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"weak\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strength").value("WEAK"));
    }

    @Test
    @DisplayName("T37 — POST /api/auth/password-strength → MEDIUM")
    void httpPasswordStrengthMedium() throws Exception {
        mockMvc.perform(post("/api/auth/password-strength")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Testpassword1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strength").value("MEDIUM"));
    }

    @Test
    @DisplayName("T38 — GET /api/me avec header sans préfixe Bearer → 401")
    void httpMeSansPrefixeBearer() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "token-sans-bearer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T39 — PUT /api/auth/change-password sans préfixe Bearer → 401")
    void httpChangePasswordSansPrefixeBearer() throws Exception {
        String json = "{\"oldPassword\":\"TestPassword1!\","
                    + "\"newPassword\":\"NouveauPassword2@\","
                    + "\"confirmPassword\":\"NouveauPassword2@\"}";
        mockMvc.perform(put("/api/auth/change-password")
                        .header("Authorization", "token-invalide-sans-bearer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T40 — PasswordPolicyValidator évaluation MEDIUM (4 critères, longueur < 16)")
    void passwordStrengthMediumAvec4Criteres() {
        assertThat(authService.evaluatePasswordStrength("TestPass1!ab")).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("T41 — PasswordPolicyValidator évaluation WEAK (longueur < 12)")
    void passwordStrengthWeakCourte() {
        assertThat(authService.evaluatePasswordStrength("Ab1!")).isEqualTo("WEAK");
    }

    @Test
    @DisplayName("T42 — PasswordPolicyValidator évaluation WEAK (null)")
    void passwordStrengthWeakNull() {
        assertThat(authService.evaluatePasswordStrength(null)).isEqualTo("WEAK");
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════

    /**
     * Effectue un login HMAC valide pour les tests.
     */
    private LoginResponse doValidLogin(String email, String password) {
        String nonce     = UUID.randomUUID().toString();
        long   timestamp = Instant.now().getEpochSecond();
        String message   = email + ":" + nonce + ":" + timestamp;
        String hmac      = hmacService.compute(password, message);
        return authService.login(new LoginRequest(email, nonce, timestamp, hmac));
    }
}

