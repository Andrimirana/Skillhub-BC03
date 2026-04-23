package com.example.auth;

import com.example.auth.service.MasterKeyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test vérifiant que l'application refuse de démarrer si APP_MASTER_KEY est absente.
 *
 * <p>Ce test instancie directement {@link MasterKeyService} sans Spring Boot
 * pour simuler une clé absente, sans impacter le contexte de test principal.</p>
 */
class MasterKeyAbsentTest {

    @Test
    @DisplayName("T28 — Démarrage KO si APP_MASTER_KEY absente → IllegalStateException")
    void demarrageSansMasterKeyEchoue() {
        MasterKeyService service = new MasterKeyService();
        // Injecter une clé vide (simule APP_MASTER_KEY absente)
        ReflectionTestUtils.setField(service, "masterKeyRaw", "");
        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_MASTER_KEY");
    }

    @Test
    @DisplayName("T29 — Démarrage KO si APP_MASTER_KEY null → IllegalStateException")
    void demarrageSansMasterKeyNullEchoue() {
        MasterKeyService service = new MasterKeyService();
        ReflectionTestUtils.setField(service, "masterKeyRaw", null);
        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_MASTER_KEY");
    }
}

