package cn.tolim.pudans.security.authentication.impl;

import static com.nimbusds.jose.jwk.KeyOperation.SIGN;
import static com.nimbusds.jose.jwk.KeyOperation.VERIFY;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import cn.tolim.pudans.security.contract.authentication.LoginCipher;
import cn.tolim.pudans.security.authentication.login.InvalidEncryptedMessageException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.crypto.codec.Hex;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class RsaLoginCipher implements LoginCipher, InitializingBean {

    public static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    public static final String OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    public static final String ALGORITHM = "RSA";

    private static final List<OAEPParameterSpec> OAEP_PARAMETERS = List.of(
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT),
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT));

    private static final String REGENERATION_WARNING =
            "生成新的密钥对将使使用旧密钥签名的 JWT 失效。";

    private final Path keysRoot;

    private KeyPair keyPair;

    private String keyId;

    private JWK jwk;

    // 仅记录密钥目录，密钥加载推迟到初始化阶段
    public RsaLoginCipher(Path dir) {
        this.keysRoot = dir;
    }

    // 仅接受模数匹配且签名验证通过的公钥，防止错配
    private static PublicKey readMatchingPublicKey(
            KeyFactory keyFactory, RSAPrivateKey privateKey, byte[] pubKeyBytes) {
        if (pubKeyBytes == null) {
            return null;
        }
        PublicKey pubKey;
        try {
            pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(pubKeyBytes));
        } catch (InvalidKeySpecException e) {
            return null;
        }
        if (!(pubKey instanceof RSAPublicKey publicKey)
                || !privateKey.getModulus().equals(publicKey.getModulus())
                || !verifyPublicKeyMatches(privateKey, pubKey)) {
            return null;
        }
        return pubKey;
    }

    // 用私钥签名再以公钥验签，确认两者确实配对
    private static boolean verifyPublicKeyMatches(PrivateKey privateKey, PublicKey publicKey) {
        try {
            var payload = ALGORITHM.getBytes(StandardCharsets.UTF_8);
            var signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(payload);
            var signed = signature.sign();
            signature.initVerify(publicKey);
            signature.update(payload);
            return signature.verify(signed);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // 读取密钥文件，文件不存在时返回空值而非报错
    private static byte[] readKeyFileIfPresent(Path path) throws IOException {
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    // 先写临时文件再原子替换，避免留下半写入内容
    private static void writeKeyFileAtomically(Path target, byte[] keyBytes) throws IOException {
        var tempPath =
                Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(tempPath, keyBytes);
            try {
                Files.move(tempPath, target, ATOMIC_MOVE, REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, target, REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    // 计算私钥摘要，用作对外暴露的密钥标识
    private static String sha256(byte[] data) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return new String(Hex.encode(md.digest(data)));
        } catch (NoSuchAlgorithmException e) {

            throw new RuntimeException("无法获取用于消息摘要的 SHA-256 算法。", e);
        }
    }

    // 启动时加载或生成密钥，并派生标识与公钥描述
    @Override
    public void afterPropertiesSet() throws JOSEException {
        this.keyPair = this.getRsaKeyPairOrCreate();
        this.keyId = sha256(keyPair.getPrivate().getEncoded());
        this.jwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey(keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .keyOperations(Set.of(SIGN, VERIFY))
                .keyIDFromThumbprint()
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    // 优先复用磁盘密钥，缺失或不匹配时重新生成落盘
    private KeyPair getRsaKeyPairOrCreate() {
        var privKeyPath = keysRoot.resolve("pat_id_rsa");
        var pubKeyPath = keysRoot.resolve("pat_id_rsa.pub");
        try {
            var keyFactory = KeyFactory.getInstance(ALGORITHM);
            var privKeyBytes = readKeyFileIfPresent(privKeyPath);
            var pubKeyBytes = readKeyFileIfPresent(pubKeyPath);
            if (privKeyBytes == null && pubKeyBytes != null) {
                log.warn("{} 处缺少 RSA 私钥。" + REGENERATION_WARNING, privKeyPath);
            }
            PrivateKey existingPrivateKey = null;
            if (privKeyBytes != null) {
                try {
                    existingPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privKeyBytes));
                } catch (InvalidKeySpecException e) {
                    log.warn("{} 处的 RSA 私钥无效。" + REGENERATION_WARNING, privKeyPath);
                }
            }
            if (existingPrivateKey instanceof RSAPrivateCrtKey privateKey) {
                var pubKey = keyFactory.generatePublic(
                        new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
                if (!Arrays.equals(pubKey.getEncoded(), pubKeyBytes)) {
                    log.warn("正在从现有私钥恢复 {} 处的 RSA 公钥。", pubKeyPath);
                    writeKeyFileAtomically(pubKeyPath, pubKey.getEncoded());
                }
                return new KeyPair(pubKey, privateKey);
            }
            if (existingPrivateKey instanceof RSAPrivateKey privateKey) {
                var pubKey = readMatchingPublicKey(keyFactory, privateKey, pubKeyBytes);
                if (pubKey != null) {
                    return new KeyPair(pubKey, privateKey);
                }
                log.warn(
                        "无法为 {} 处的非 CRT RSA 私钥派生或验证公钥。 "
                                + REGENERATION_WARNING,
                        privKeyPath);
            }

            log.info("正在生成 PAT 的 RSA 密钥。");
            var rsaKey = new RSAKeyGenerator(4096).generate();
            var pubKey = rsaKey.toRSAPublicKey();
            var privKey = rsaKey.toRSAPrivateKey();
            Files.createDirectories(keysRoot);

            writeKeyFileAtomically(privKeyPath, privKey.getEncoded());
            writeKeyFileAtomically(pubKeyPath, pubKey.getEncoded());
            log.info("已将 PAT 的 RSA 密钥写入 {} 和 {}", privKeyPath, pubKeyPath);
            return new KeyPair(pubKey, privKey);
        } catch (JOSEException | IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new RuntimeException("无法生成或读取 RSA 密钥对", e);
        }
    }

    // 优先按 OAEP 解密，并保留 PKCS#1 以兼容旧客户端
    @Override
    public Mono<byte[]> decrypt(byte[] encryptedMessage) {
        return Mono.just(this.keyPair)
                .map(KeyPair::getPrivate)
                .map(privateKey -> decrypt(privateKey, encryptedMessage))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // 依次尝试不同 OAEP 参数，最后回退到 PKCS#1 填充
    private static byte[] decrypt(PrivateKey privateKey, byte[] encryptedMessage) {
        for (var parameters : OAEP_PARAMETERS) {
            try {
                var cipher = Cipher.getInstance(OAEP_TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, privateKey, parameters);
                return cipher.doFinal(encryptedMessage);
            } catch (BadPaddingException | IllegalBlockSizeException ignored) {
                // 当前填充方式不匹配，继续尝试下一种
            } catch (GeneralSecurityException error) {
                throw new IllegalStateException("RSA 登录解密配置无效。", error);
            }
        }
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(encryptedMessage);
        } catch (BadPaddingException | IllegalBlockSizeException error) {
            throw new InvalidEncryptedMessageException("加密消息无效。", error);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("RSA 登录解密配置无效。", error);
        }
    }

    // 返回编码后的公钥，供客户端加密登录报文
    @Override
    public Mono<byte[]> readPublicKey() {
        return Mono.just(keyPair).map(KeyPair::getPublic).map(PublicKey::getEncoded);
    }

    // 返回私钥派生的标识，供外部定位对应密钥
    @Override
    public String getKeyId() {
        return this.keyId;
    }

    // 暴露密钥描述，供令牌签名与校验共用
    @Override
    public JWK getJwk() {
        return this.jwk;
    }
}
