package cn.tolim.pudans.security.authentication.twofactor.totp;

import static com.j256.twofactorauth.TimeBasedOneTimePasswordUtil.generateBase32Secret;

import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.infra.properties.PudansProperties;

@Slf4j
@Component
public class DefaultTotpSecrets implements TotpSecrets {

    private static final String ALIAS = "totp-secret-key";

    private static final String KEYSTORE_FILE = "pudans.keystore";

    private static final String PASSWORD_FILE = "pudans.keystore.password";

    private static final int KEY_SIZE_BITS = 256;

    // 允许相邻一个时间步的时钟偏差，{前后各 30 秒}
    private static final int TOTP_STEP_TOLERANCE = 1;

    private static final int TOTP_TIME_STEP_SECONDS =
            TimeBasedOneTimePasswordUtil.DEFAULT_TIME_STEP_SECONDS;

    private final BytesEncryptor encryptor;

    // 优先使用外部密钥，否则加载或创建设备本地密钥库。
    public DefaultTotpSecrets(PudansProperties properties) {
        var options = properties.getSecurity().getTotp();
        if (StringUtils.hasText(options.getKey())) {
            this.encryptor = fromExternalKey(options.getKey());
        } else {
            this.encryptor = loadOrCreateEncryptor(
                    properties.getWorkDir().resolve("keys"), options.getKeystorePassword());
        }
    }

    // 校验外部 AES 密钥格式，配置错误时在启动阶段失败。
    private static BytesEncryptor fromExternalKey(String encodedKey) {
        final byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "pudans.security.totp.key 必须是 Base64 编码的 16、24 或 32 字节 AES 密钥。", e);
        }
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException("pudans.security.totp.key 解码后必须为 16、24 或 32 字节。");
        }
        return encryptorOf(new SecretKeySpec(key, "AES"));
    }

    // 复用已有密钥库，条目缺失或无效时生成新密钥并原子落盘。
    private BytesEncryptor loadOrCreateEncryptor(Path keysRoot, String configuredPassword) {
        try {
            Files.createDirectories(keysRoot);
            var keyStorePath = keysRoot.resolve(KEYSTORE_FILE);
            var passwordFile = keysRoot.resolve(PASSWORD_FILE);
            var password = resolvePassword(configuredPassword, passwordFile);
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (Files.notExists(keyStorePath)) {
                keyStore.load(null, password);
                var generated = generateKey();
                storeEntry(keyStore, generated, password);
                writeAtomically(keyStorePath, keyStore, password);
                return encryptorOf(generated);
            }
            try (var input = Files.newInputStream(keyStorePath, StandardOpenOption.READ)) {
                keyStore.load(input, password);
            }
            var entry = keyStore.getEntry(ALIAS, new KeyStore.PasswordProtection(password));
            if (entry instanceof KeyStore.SecretKeyEntry secretKeyEntry
                    && "AES".equalsIgnoreCase(secretKeyEntry.getSecretKey().getAlgorithm())) {
                return encryptorOf(secretKeyEntry.getSecretKey());
            }
            var generated = generateKey();
            storeEntry(keyStore, generated, password);
            writeAtomically(keyStorePath, keyStore, password);
            return encryptorOf(generated);
        } catch (IOException
                | KeyStoreException
                | CertificateException
                | NoSuchAlgorithmException
                | UnrecoverableEntryException e) {
            throw new IllegalStateException(
                    "无法打开位于 " + keysRoot + " 的 TOTP 密钥库。请配置 pudans.security.totp.key"
                            + "（Base64 编码的 AES 密钥，推荐用于多副本部署），或配置"
                            + " pudans.security.totp.keystore-password，将其设为保护现有密钥库的密码。",
                    e);
        }
    }

    // 用配置密码，否则生成并持久化随机的密钥库密码。
    private static char[] resolvePassword(String configuredPassword, Path passwordFile) throws IOException {
        if (StringUtils.hasText(configuredPassword)) {
            return configuredPassword.toCharArray();
        }
        if (Files.notExists(passwordFile)) {
            var generated = KeyGenerators.string().generateKey() + KeyGenerators.string().generateKey();
            Files.writeString(
                    passwordFile,
                    generated,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            restrictPermissions(passwordFile);
            return generated.toCharArray();
        }
        return Files.readString(passwordFile).trim().toCharArray();
    }

    // 以密码保护方式写入 AES 密钥条目，供后续读取。
    private static void storeEntry(KeyStore keyStore, SecretKey key, char[] password) throws KeyStoreException {
        keyStore.setEntry(ALIAS, new KeyStore.SecretKeyEntry(key), new KeyStore.PasswordProtection(password));
    }

    // 生成 256 位 AES 密钥，作为 TOTP 密钥的加密根密钥。
    private static SecretKey generateKey() throws NoSuchAlgorithmException {
        var generator = KeyGenerator.getInstance("AES");
        generator.init(KEY_SIZE_BITS);
        return generator.generateKey();
    }

    // 先写临时文件再原子替换，避免密钥库写入中途损坏。
    private static void writeAtomically(Path target, KeyStore keyStore, char[] password)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        var directory = target.getParent();
        var temporary = Files.createTempFile(directory, "pudans-keystore", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.WRITE)) {
                keyStore.store(output, password);
            }
            restrictPermissions(temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    // 尽力限制为仅属主可读写，平台不支持时降级放行。
    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("无法限制 {} 的权限", path);
        }
    }

    // 统一使用 GCM 模式加密，保证密文完整性与随机性。
    private static BytesEncryptor encryptorOf(SecretKey secretKey) {
        return new AesBytesEncryptor(
                secretKey, KeyGenerators.secureRandom(32), AesBytesEncryptor.CipherAlgorithm.GCM);
    }

    // 在允许的时间步偏差内匹配验证码，命中时返回对应步长。
    @Override
    public Long matchStep(String rawSecret, int code, long nowMillis) {
        try {
            var currentStep = nowMillis / 1000 / TOTP_TIME_STEP_SECONDS;
            var start = currentStep - TOTP_STEP_TOLERANCE;
            var end = currentStep + TOTP_STEP_TOLERANCE;
            for (var step = end; step >= start; step--) {
                var generated = TimeBasedOneTimePasswordUtil.generateNumber(
                        rawSecret, step * 1000L * TOTP_TIME_STEP_SECONDS, TOTP_TIME_STEP_SECONDS);
                if (generated == code) {
                    return step;
                }
            }
            return null;
        } catch (GeneralSecurityException e) {
            log.warn("校验 TOTP 验证码时发生错误", e);
            return null;
        }
    }

    // 生成 32 位 Base32 密钥，供认证器应用绑定。
    @Override
    public String generateTotpSecret() {
        return generateBase32Secret(32);
    }

    // 以十六进制输出密文，便于安全地存入扩展字段。
    @Override
    public String encryptSecret(String rawSecret) {
        return new String(Hex.encode(encryptor.encrypt(rawSecret.getBytes())));
    }

    // 还原明文密钥，仅供服务端校验时短暂使用。
    @Override
    public String decryptSecret(String encryptedSecret) {
        return new String(encryptor.decrypt(Hex.decode(encryptedSecret)));
    }
}
