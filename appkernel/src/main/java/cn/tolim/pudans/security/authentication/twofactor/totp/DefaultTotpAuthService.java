package cn.tolim.pudans.security.authentication.twofactor.totp;

import static com.j256.twofactorauth.TimeBasedOneTimePasswordUtil.generateBase32Secret;
import static com.j256.twofactorauth.TimeBasedOneTimePasswordUtil.validateCurrentNumber;

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
public class DefaultTotpAuthService implements TotpAuthService {

    private static final String ALIAS = "totp-secret-key";

    private static final String KEYSTORE_FILE = "pudans.keystore";

    private static final String PASSWORD_FILE = "pudans.keystore.password";

    private static final int KEY_SIZE_BITS = 256;

    private final BytesEncryptor encryptor;

    public DefaultTotpAuthService(PudansProperties properties) {
        var options = properties.getSecurity().getTotp();
        if (StringUtils.hasText(options.getKey())) {
            this.encryptor = fromExternalKey(options.getKey());
        } else {
            this.encryptor = loadOrCreateEncryptor(
                    properties.getWorkDir().resolve("keys"), options.getKeystorePassword());
        }
    }

    private static BytesEncryptor fromExternalKey(String encodedKey) {
        final byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "pudans.security.totp.key must be a base64 encoded 16, 24 or 32 byte AES key.", e);
        }
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException("pudans.security.totp.key must decode to 16, 24 or 32 bytes.");
        }
        return encryptorOf(new SecretKeySpec(key, "AES"));
    }

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
                    "Unable to open the TOTP keystore at " + keysRoot + ". Configure pudans.security.totp.key"
                            + " (base64 AES key, recommended for multi replica deployments) or"
                            + " pudans.security.totp.keystore-password with the password that protects the"
                            + " existing keystore.",
                    e);
        }
    }

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

    private static void storeEntry(KeyStore keyStore, SecretKey key, char[] password) throws KeyStoreException {
        keyStore.setEntry(ALIAS, new KeyStore.SecretKeyEntry(key), new KeyStore.PasswordProtection(password));
    }

    private static SecretKey generateKey() throws NoSuchAlgorithmException {
        var generator = KeyGenerator.getInstance("AES");
        generator.init(KEY_SIZE_BITS);
        return generator.generateKey();
    }

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

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("Unable to restrict the permissions of {}", path);
        }
    }

    private static BytesEncryptor encryptorOf(SecretKey secretKey) {
        return new AesBytesEncryptor(
                secretKey, KeyGenerators.secureRandom(32), AesBytesEncryptor.CipherAlgorithm.GCM);
    }

    @Override
    public boolean validateTotp(String rawSecret, int code) {
        try {
            return validateCurrentNumber(rawSecret, code, 10 * 1000);
        } catch (GeneralSecurityException e) {
            log.warn("Error occurred when validate TOTP code", e);
            return false;
        }
    }

    @Override
    public String generateTotpSecret() {
        return generateBase32Secret(32);
    }

    @Override
    public String encryptSecret(String rawSecret) {
        return new String(Hex.encode(encryptor.encrypt(rawSecret.getBytes())));
    }

    @Override
    public String decryptSecret(String encryptedSecret) {
        return new String(encryptor.decrypt(Hex.decode(encryptedSecret)));
    }
}
