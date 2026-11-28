package cn.tolim.pudans.repository.security;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryException;
import cn.tolim.pudans.repository.service.ChecksumService;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import cn.tolim.spissatus4j.core.builder.SpissatusBuilders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpissatusSecretCipherTests {

    private static Spissatus spissatus;

    @BeforeAll
    static void createFacade() {
        spissatus = SpissatusBuilders.builder()
                .discoverModules(true)
                .failClosed(true)
                .build();
    }

    @AfterAll
    static void closeFacade() {
        if (spissatus != null) {
            spissatus.close();
        }
    }

    @Test
    void encryptsAndDecryptsSecrets() {
        SpissatusSecretCipher cipher = new SpissatusSecretCipher(spissatus, cryptoProperties());

        String encrypted = cipher.encrypt("access-key-secret");

        assertThat(encrypted).startsWith(SpissatusSecretCipher.PREFIX);
        assertThat(encrypted).doesNotContain("access-key-secret");
        assertThat(cipher.decryptIfEncrypted(encrypted)).isEqualTo("access-key-secret");
        assertThat(cipher.currentVersion()).isNotNull();
    }

    @Test
    void passesThroughPlainValues() {
        SpissatusSecretCipher cipher = new SpissatusSecretCipher(spissatus, cryptoProperties());

        assertThat(cipher.decryptIfEncrypted("plain-secret")).isEqualTo("plain-secret");
        assertThat(cipher.decryptIfEncrypted(null)).isNull();
        assertThat(cipher.decryptIfEncrypted("")).isEmpty();
    }

    @Test
    void rejectsEmptyPlaintext() {
        SpissatusSecretCipher cipher = new SpissatusSecretCipher(spissatus, cryptoProperties());

        assertThatThrownBy(() -> cipher.encrypt(""))
                .isInstanceOf(RepositoryException.class);
    }

    @Test
    void computesSha256ThroughFacade() {
        RepositoryProperties properties = new RepositoryProperties();
        properties.getChecksum().setEnabled(true);
        properties.getChecksum().setMaxBytes(64);
        ChecksumService checksumService = new ChecksumService(spissatus, properties);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        String checksum = checksumService.sha256(() -> new ByteArrayInputStream(content),
                content.length);

        assertThat(checksum)
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(checksumService.sha256(() -> new ByteArrayInputStream(content), 128)).isNull();
    }

    @Test
    void plaintextCipherRejectsEncryptedValues() {
        PlaintextSecretCipher cipher = new PlaintextSecretCipher();

        assertThat(cipher.decryptIfEncrypted("plain-secret")).isEqualTo("plain-secret");
        assertThatThrownBy(() -> cipher.decryptIfEncrypted(PlaintextSecretCipher.PREFIX + "value"))
                .isInstanceOf(RepositoryException.class);
        assertThatThrownBy(() -> cipher.encrypt("value"))
                .isInstanceOf(RepositoryException.class);
    }

    private static RepositoryProperties.Crypto cryptoProperties() {
        RepositoryProperties.Crypto properties = new RepositoryProperties.Crypto();
        properties.setKeyId("test-repository-secret");
        properties.setTenantId("test-tenant");
        properties.setCallerId("test-caller");
        properties.setCallerEnvironment("test");
        return properties;
    }
}