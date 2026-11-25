package cn.tolim.pudans.infra.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// 集中提供摘要算法，避免各业务重复实现同一套编码和异常映射。
public final class Digests {

    private static final HexFormat HEX = HexFormat.of();

    private static final String SHA_256 = "SHA-256";

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private Digests() {}

    // 返回 UTF-8 文本的 SHA-256 十六进制摘要。
    public static String sha256Hex(String value) {
        Objects.requireNonNull(value, "摘要内容不能为空");
        try {
            return HEX.formatHex(MessageDigest.getInstance(SHA_256).digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static String sha256Hex(byte[] value) {
        Objects.requireNonNull(value, "摘要内容不能为空");
        try {
            return HEX.formatHex(MessageDigest.getInstance(SHA_256).digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    // 分块计算文件摘要，避免把大文件整体读入内存。
    public static String sha256Hex(Path file) {
        Objects.requireNonNull(file, "摘要文件不能为空");
        try (InputStream input = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance(SHA_256);
            var buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HEX.formatHex(digest.digest());
        } catch (IOException error) {
            throw new IllegalStateException("无法读取摘要文件", error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    // 密钥为空时退化为普通 SHA-256，兼容未配置主密钥的旧令牌。
    public static String hmacSha256Hex(String key, String value) {
        Objects.requireNonNull(value, "摘要内容不能为空");
        if (key == null || key.isBlank()) {
            return sha256Hex(value);
        }
        try {
            var mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return HEX.formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法使用主密钥进行签名", e);
        }
    }

    // 常量时间比较摘要，避免按字节短路暴露匹配长度。
    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
