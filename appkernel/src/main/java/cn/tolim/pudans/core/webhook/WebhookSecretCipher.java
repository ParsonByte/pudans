package cn.tolim.pudans.core.webhook;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import cn.tolim.pudans.core.infra.properties.PudansProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class WebhookSecretCipher {

    private static final String PREFIX = "enc:";

    // 兼容密码可配置之前创建的密钥库的一次性迁移路径。
    private static final char[] LEGACY_PASSWORD = "changeit".toCharArray();

    private static final String ALIAS = "webhook-secret-key";

    private static final String KEYSTORE_FILE = "webhook.keystore";

    private static final String SECURE_PERMISSIONS = "rw-------";

    private final BytesEncryptor encryptor;

    public WebhookSecretCipher(PudansProperties properties) {
        var configuredPassword = properties.getWebhook().getKeystorePassword();
        if (!StringUtils.hasText(configuredPassword)) {
            throw new IllegalStateException(
                    "必须设置 pudans.webhook.keystore-password (PUDANS_WEBHOOK_KEYSTORE_PASSWORD)。");
        }
        this.encryptor = loadOrCreate(
                properties.getWorkDir().resolve("keys"), configuredPassword.toCharArray());
    }

    private static BytesEncryptor loadOrCreate(Path keysRoot, char[] password) {
        try {
            if (Files.notExists(keysRoot)) {
                Files.createDirectories(keysRoot);
            }
            var keyStorePath = keysRoot.resolve(KEYSTORE_FILE);
            if (Files.notExists(keyStorePath)) {
                return createNew(keyStorePath, password);
            }
            var keyStore = load(keyStorePath, password);
            if (keyStore != null) {
                warnIfInsecurePermissions(keyStorePath);
                return encryptorWithExistingKey(keyStore, password, keyStorePath);
            }
            var legacyKeyStore = load(keyStorePath, LEGACY_PASSWORD);
            if (legacyKeyStore == null) {
                throw new IllegalStateException(
                        "无法打开 Webhook 密钥库，请检查 pudans.webhook.keystore-password。");
            }
            var legacyKey = getAesKey(legacyKeyStore, LEGACY_PASSWORD);
            if (legacyKey == null) {
                throw new IllegalStateException(
                        "Webhook 密钥库中不包含可用旧密码读取的 AES 密钥。");
            }
            legacyKeyStore.setEntry(
                    ALIAS,
                    new KeyStore.SecretKeyEntry(legacyKey),
                    new KeyStore.PasswordProtection(password));
            store(legacyKeyStore, keyStorePath, password);
            restrictPermissions(keyStorePath);
            log.warn(
                    "已将 Webhook 密钥库迁移到配置的密码，现有 Webhook 密钥仍可解密。");
            return encryptor(legacyKey);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("无法初始化 Webhook 密钥加密器", e);
        }
    }

    private static BytesEncryptor createNew(Path keyStorePath, char[] password)
            throws GeneralSecurityException, IOException {
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, password);
        var secretKey = generateKey();
        keyStore.setEntry(
                ALIAS, new KeyStore.SecretKeyEntry(secretKey), new KeyStore.PasswordProtection(password));
        store(keyStore, keyStorePath, password);
        restrictPermissions(keyStorePath);
        return encryptor(secretKey);
    }

    private static BytesEncryptor encryptorWithExistingKey(
            KeyStore keyStore, char[] password, Path keyStorePath) throws GeneralSecurityException {
        var secretKey = getAesKey(keyStore, password);
        if (secretKey == null) {
            throw new IllegalStateException(
                    "Webhook 密钥库中不包含可用配置密码读取的 AES 密钥。"
                            + "请检查 pudans.webhook.keystore-password（" + keyStorePath + "）。");
        }
        return encryptor(secretKey);
    }

    private static KeyStore load(Path keyStorePath, char[] password) {
        try {
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (var is = Files.newInputStream(keyStorePath, READ)) {
                keyStore.load(is, password);
                return keyStore;
            }
        } catch (IOException | GeneralSecurityException e) {
            return null;
        }
    }

    private static SecretKey getAesKey(KeyStore keyStore, char[] password) throws GeneralSecurityException {
        var entry = keyStore.getEntry(ALIAS, new KeyStore.PasswordProtection(password));
        if (entry instanceof KeyStore.SecretKeyEntry secretKeyEntry
                && "AES".equalsIgnoreCase(secretKeyEntry.getSecretKey().getAlgorithm())) {
            return secretKeyEntry.getSecretKey();
        }
        return null;
    }

    private static SecretKey generateKey() throws GeneralSecurityException {
        var generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    private static void store(KeyStore keyStore, Path keyStorePath, char[] password)
            throws GeneralSecurityException, IOException {
        try (var os = Files.newOutputStream(keyStorePath, CREATE, TRUNCATE_EXISTING)) {
            keyStore.store(os, password);
        }
    }

    private static BytesEncryptor encryptor(SecretKey secretKey) {
        return new AesBytesEncryptor(
                secretKey, KeyGenerators.secureRandom(32), AesBytesEncryptor.CipherAlgorithm.GCM);
    }

    private static void restrictPermissions(Path keyStorePath) {
        try {
            Files.setPosixFilePermissions(keyStorePath, PosixFilePermissions.fromString(SECURE_PERMISSIONS));
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("无法设置 Webhook 密钥库 {} 的权限", keyStorePath, e);
        }
    }

    private static void warnIfInsecurePermissions(Path keyStorePath) {
        try {
            var permissions = Files.getPosixFilePermissions(keyStorePath);
            var expected = PosixFilePermissions.fromString(SECURE_PERMISSIONS);
            if (!permissions.equals(expected)) {
                log.warn(
                        "Webhook 密钥库 [{}] 的权限为 [{}]；预期为 [{}]。",
                        keyStorePath,
                        PosixFilePermissions.toString(permissions),
                        SECURE_PERMISSIONS);
            }
        } catch (UnsupportedOperationException e) {
            log.debug("此平台不支持 POSIX 权限");
        } catch (IOException e) {
            log.warn("无法检查 Webhook 密钥库 {} 的权限", keyStorePath, e);
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
