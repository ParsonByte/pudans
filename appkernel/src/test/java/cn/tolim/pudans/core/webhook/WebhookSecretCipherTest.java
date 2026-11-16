package cn.tolim.pudans.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.infra.properties.PudansProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebhookSecretCipherTest {

    @TempDir
    Path workDir;

    private PudansProperties properties(String password) {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        properties.getWebhook().setKeystorePassword(password);
        return properties;
    }

    @Test
    void configuredPasswordIsRequired() {
        assertThatThrownBy(() -> new WebhookSecretCipher(properties("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keystore-password");
    }

    @Test
    void configuredPasswordProtectsTheKeystore() {
        var cipher = new WebhookSecretCipher(properties("correct-horse"));
        var encrypted = cipher.encrypt("webhook-secret");

        var reopened = new WebhookSecretCipher(properties("correct-horse"));
        assertThat(reopened.decrypt(encrypted)).isEqualTo("webhook-secret");

        assertThatThrownBy(() -> new WebhookSecretCipher(properties("wrong-password")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void legacyKeystoreIsMigratedToTheConfiguredPassword() throws Exception {
        createLegacyKeystore();

        var migrated = new WebhookSecretCipher(properties("new-password"));
        var encrypted = migrated.encrypt("legacy-secret");
        assertThat(new WebhookSecretCipher(properties("new-password")).decrypt(encrypted))
                .isEqualTo("legacy-secret");
        assertThatThrownBy(() -> new WebhookSecretCipher(properties("changeit")))
                .isInstanceOf(IllegalStateException.class);
    }

    private void createLegacyKeystore() throws Exception {
        var keysRoot = workDir.resolve("keys");
        Files.createDirectories(keysRoot);
        var keyStorePath = keysRoot.resolve("webhook.keystore");
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        var legacyPassword = "changeit".toCharArray();
        keyStore.load(null, legacyPassword);
        var generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        keyStore.setEntry(
                "webhook-secret-key",
                new KeyStore.SecretKeyEntry(generator.generateKey()),
                new KeyStore.PasswordProtection(legacyPassword));
        try (var os = Files.newOutputStream(keyStorePath)) {
            keyStore.store(os, legacyPassword);
        }
    }
}
