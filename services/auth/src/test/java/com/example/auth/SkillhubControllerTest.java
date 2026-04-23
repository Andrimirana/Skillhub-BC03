package com.example.auth;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.repository.AccessTokenRepository;
import com.example.auth.repository.AuthNonceRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthService;
import com.example.auth.service.HmacService;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration — SkillhubController.
 * Couvre les endpoints /api/register, /api/login, /api/profil,
 * /api/logout, /api/change-password, /api/validate-token, /api/health.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillhubControllerTest {

    @Autowired private MockMvc               mockMvc;
    @Autowired private AuthService           authService;
    @Autowired private HmacService           hmacService;
    @Autowired private UserRepository        userRepository;
    @Autowired private AccessTokenRepository tokenRepository;
    @Autowired private AuthNonceRepository   nonceRepository;

    private static final String EMAIL    = "skillhub@example.com";
    private static final String PASSWORD = "TestPassword1!";

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        nonceRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── /api/health ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("S01 — GET /api/health → 200 {status: UP}")
    void healthOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── /api/register ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("S02 — POST /api/register OK → 201 avec token et utilisateur")
    void registerOk() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nom":"Alice","email":"alice@example.com",
                             "password":"TestPassword1!","passwordConfirm":"TestPassword1!",
                             "role":"formateur"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.utilisateur.email").value("alice@example.com"))
                .andExpect(jsonPath("$.utilisateur.role").value("formateur"))
                .andExpect(jsonPath("$.utilisateur.nom").value("Alice"));
    }

    @Test
    @DisplayName("S03 — POST /api/register sans rôle → role par défaut apprenant")
    void registerSansRoleDefautApprenant() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nom":"Bob","email":"bob@example.com",
                             "password":"TestPassword1!","passwordConfirm":"TestPassword1!"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.utilisateur.role").value("apprenant"));
    }

    @Test
    @DisplayName("S04 — POST /api/register email invalide → 400")
    void registerEmailInvalide() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nom":"X","email":"pasunemail",
                             "password":"TestPassword1!","passwordConfirm":"TestPassword1!"}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("S05 — POST /api/register email déjà pris → 409")
    void registerEmailDejaExistant() throws Exception {
        authService.registerSkillhubUser(EMAIL, PASSWORD, PASSWORD, "Test", "apprenant");
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nom":"Test","email":"skillhub@example.com",
                             "password":"TestPassword1!","passwordConfirm":"TestPassword1!"}
                            """))
                .andExpect(status().isConflict());
    }

    // ── /api/login ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("S06 — POST /api/login HMAC valide → 200 avec token Skillhub")
    void loginOk() throws Exception {
        authService.registerSkillhubUser(EMAIL, PASSWORD, PASSWORD, "Test", "apprenant");
        String json = buildLoginJson(EMAIL, PASSWORD);
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.utilisateur.email").value(EMAIL))
                .andExpect(jsonPath("$.expiresAt").isNumber());
    }

    @Test
    @DisplayName("S07 — POST /api/login HMAC invalide → 401")
    void loginHmacInvalide() throws Exception {
        authService.registerSkillhubUser(EMAIL, PASSWORD, PASSWORD, "Test", "apprenant");
        String nonce     = UUID.randomUUID().toString();
        long   timestamp = Instant.now().getEpochSecond();
        String json = String.format(
            "{\"email\":\"%s\",\"nonce\":\"%s\",\"timestamp\":%d,\"hmac\":\"invalide\"}",
            EMAIL, nonce, timestamp);
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    // ── /api/profil ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("S08 — GET /api/profil avec token valide → 200 avec nom et rôle")
    void profilOk() throws Exception {
        authService.registerSkillhubUser(EMAIL, PASSWORD, PASSWORD, "Test User", "formateur");
        String token = doLogin(EMAIL, PASSWORD);
        mockMvc.perform(get("/api/profil")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nom").value("Test User"))
                .andExpect(jsonPath("$.role").value("formateur"));
    }

    @Test
    @DisplayName("S09 — GET /api/profil token invalide → 401")
    void profilTokenInvalide() throws Exception {
        mockMvc.perform(get("/api/profil")
                        .header("Authorization", "Bearer token-invalide"))
                .andExpect(status().isUnauthorized());
    }

    // ── /api/logout ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("S10 — POST /api/logout → 200 message déconnexion")
    void logoutOk() throws Exception {
        authService.registerSkillhubUser(EMAIL, PASSWORD, PASSWORD, "Test", "apprenant");
        String token = doLogin(EMAIL, PASSWORD);
        mockMvc.perform(post("/api/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Déconnexion effectuée."));
    }

    // ── /api/change-password ──────────────────────────────────────────────────

    @Test
    @DisplayName("S11 — PUT /api/change-password OK → 200")
    void changePasswordOk() throws Exception {
        authService.registerSkillhubUser(EMAIL, PASSWORD, PASSWORD, "Test", "apprenant");
        String token = doLogin(EMAIL, PASSWORD);
        mockMvc.perform(put("/api/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"oldPassword":"TestPassword1!",
                             "newPassword":"NouveauPass2@",
                             "confirmPassword":"NouveauPass2@"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mot de passe modifié avec succès."));
    }

    @Test
    @DisplayName("S12 — PUT /api/change-password ancien MDP incorrect → 401")
    void changePasswordAncienIncorrect() throws Exception {
        authService.registerSkillhubUser(EMAIL, PASSWORD, PASSWORD, "Test", "apprenant");
        String token = doLogin(EMAIL, PASSWORD);
        mockMvc.perform(put("/api/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"oldPassword":"MauvaisAncien1!",
                             "newPassword":"NouveauPass2@",
                             "confirmPassword":"NouveauPass2@"}
                            """))
                .andExpect(status().isUnauthorized());
    }

    // ── /api/validate-token ───────────────────────────────────────────────────

    @Test
    @DisplayName("S13 — POST /api/validate-token valide → 200 {valid:true, user}")
    void validateTokenValide() throws Exception {
        authService.registerSkillhubUser(EMAIL, PASSWORD, PASSWORD, "Test", "apprenant");
        String token = doLogin(EMAIL, PASSWORD);
        mockMvc.perform(post("/api/validate-token")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.nom").isNotEmpty())
                .andExpect(jsonPath("$.user.role").isNotEmpty());
    }

    @Test
    @DisplayName("S14 — POST /api/validate-token invalide → 401 {valid:false}")
    void validateTokenInvalide() throws Exception {
        mockMvc.perform(post("/api/validate-token")
                        .header("Authorization", "Bearer token-invalide"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("S15 — POST /api/validate-token sans header → 401 {valid:false}")
    void validateTokenSansHeader() throws Exception {
        mockMvc.perform(post("/api/validate-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("S16 — POST /api/validate-token header sans préfixe Bearer → 401")
    void validateTokenSansPrefixeBearer() throws Exception {
        mockMvc.perform(post("/api/validate-token")
                        .header("Authorization", "token-sans-bearer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String doLogin(String email, String password) {
        String nonce     = UUID.randomUUID().toString();
        long   timestamp = Instant.now().getEpochSecond();
        String message   = email + ":" + nonce + ":" + timestamp;
        String hmac      = hmacService.compute(password, message);
        LoginResponse resp = authService.login(new LoginRequest(email, nonce, timestamp, hmac));
        return resp.accessToken();
    }

    private String buildLoginJson(String email, String password) {
        String nonce     = UUID.randomUUID().toString();
        long   timestamp = Instant.now().getEpochSecond();
        String message   = email + ":" + nonce + ":" + timestamp;
        String hmac      = hmacService.compute(password, message);
        return String.format(
            "{\"email\":\"%s\",\"nonce\":\"%s\",\"timestamp\":%d,\"hmac\":\"%s\"}",
            email, nonce, timestamp, hmac);
    }
}
