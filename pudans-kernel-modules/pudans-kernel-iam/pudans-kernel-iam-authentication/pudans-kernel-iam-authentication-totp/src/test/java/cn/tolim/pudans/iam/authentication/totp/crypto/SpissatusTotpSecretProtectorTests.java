package cn.tolim.pudans.iam.authentication.totp.crypto;

import cn.tolim.pudans.iam.authentication.totp.config.TotpAuthenticationProperties;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpSecretUnavailableException;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpissatusTotpSecretProtectorTests {

    private SpissatusTotpSecretProtector protector;

    @BeforeEach
    void setUp() {
        TotpAuthenticationProperties properties = new TotpAuthenticationProperties();
        properties.getCrypto().setKeyProvider("memory");
        protector = new SpissatusTotpSecretProtector(emptyProvider(), properties);
    }

    @AfterEach
    void tearDown() {
        protector.close();
    }

    @Test
    void encryptsAndDecryptsSecret() {
        byte[] secret = protector.generateSecret(20);
        assertEquals(20, secret.length);
        TotpSecretEnvelope envelope = protector.protect("user-1", secret);
        assertEquals("AES-GCM", envelope.algorithm());
        assertFalse(envelope.ciphertext().isEmpty());
        assertArrayEquals(secret, protector.unprotect("user-1", envelope));
    }

    @Test
    void bindsCiphertextToUser() {
        TotpSecretEnvelope envelope = protector.protect("user-1",
                "secret-material".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThrows(TotpSecretUnavailableException.class,
                () -> protector.unprotect("user-2", envelope));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> protector.generateSecret(4));
        assertThrows(IllegalArgumentException.class,
                () -> protector.protect(" ", new byte[20]));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Spissatus> emptyProvider() {
        ObjectProvider<Spissatus> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}