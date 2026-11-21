package cn.tolim.pudans.infra.crypto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacTokenSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    // 加载持久化密钥用于 HMAC 签名。
    public HmacTokenSigner(Path keyFile) {
        this.key = new SecretKeySpec(KeyFileStore.loadOrCreate(keyFile), ALGORITHM);
    }

    // 生成 URL 安全的无填充签名。
    public String sign(String payload) {
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            var signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法生成存储令牌签名", e);
        }
    }

    // 使用常量时间比较，避免签名侧信道泄露。
    public boolean verify(String payload, String signature) {
        if (payload == null || signature == null) {
            return false;
        }
        var expected = sign(payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }
}
