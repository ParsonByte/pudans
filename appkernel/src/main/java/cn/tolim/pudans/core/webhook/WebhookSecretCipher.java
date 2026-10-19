package cn.tolim.pudans.core.webhook;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;

import cn.tolim.pudans.infra.properties.PudansProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;

@Component
public class WebhookSecretCipher {

    private static final String PREFIX = "enc:";

    private static final char[] PASSWORD = "changeit".toCharArray();

    private static final String ALIAS = "webhook-secret-key";

    private final BytesEncryptor encryptor;

    public WebhookSecretCipher(PudansProperties properties) {
        this.encryptor = loadOrCreate(properties.getWorkDir().resolve("keys"));
    }

    private static BytesEncryptor loadOrCreate(Path keysRoot) {
        try {
            if (Files.notExists(keysRoot)) {
                Files.createDirectories(keysRoot);
            }
            var keyStorePath = keysRoot.resolve("webhook.keystore");
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (Files.notExists(keyStorePath)) {
                keyStore.load(null, PASSWORD);
            } else {
                try (var is = Files.newInputStream(keyStorePath, READ)) {
                    keyStore.load(is, PASSWORD);
                }
            }
            var entry = keyStore.getEntry(ALIAS, new KeyStore.PasswordProtection(PASSWORD));
            SecretKey secretKey = null;
            if (entry instanceof KeyStore.SecretKeyEntry secretKeyEntry
                    && "AES".equalsIgnoreCase(secretKeyEntry.getSecretKey().getAlgorithm())) {
                secretKey = secretKeyEntry.getSecretKey();
            }
            if (secretKey == null) {
                var generator = KeyGenerator.getInstance("AES");
                generator.init(256);
                secretKey = generator.generateKey();
                keyStore.setEntry(ALIAS, new KeyStore.SecretKeyEntry(secretKey),
                        new KeyStore.PasswordProtection(PASSWORD));
                try (var os = Files.newOutputStream(keyStorePath, CREATE)) {
                    keyStore.store(os, PASSWORD);
                }
            }
            return new AesBytesEncryptor(secretKey, KeyGenerators.secureRandom(32),
                    AesBytesEncryptor.CipherAlgorithm.GCM);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Unable to initialize webhook secret cipher", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String rawSecret) {
        return PREFIX + new String(Hex.encode(encryptor.encrypt(rawSecret.getBytes(StandardCharsets.UTF_8))));
    }

    public String decrypt(String storedSecret) {
        if (!isEncrypted(storedSecret)) {
            return storedSecret;
        }
        var encoded = storedSecret.substring(PREFIX.length());
        return new String(encryptor.decrypt(Hex.decode(encoded)), StandardCharsets.UTF_8);
    }
}
