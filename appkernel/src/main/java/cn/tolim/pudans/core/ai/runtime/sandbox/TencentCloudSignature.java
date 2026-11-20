package cn.tolim.pudans.core.ai.runtime.sandbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// 腾讯云 API 3.0 TC3-HMAC-SHA256 签名。
final class TencentCloudSignature {

    private static final String ALGORITHM = "TC3-HMAC-SHA256";

    private static final String SERVICE = "ags";

    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private TencentCloudSignature() {}

    static String authorization(
            String secretId, String secretKey, String action, String host, long timestamp, String payload) {
        var date = DATE.format(Instant.ofEpochSecond(timestamp));
        var canonicalHeaders = "content-type:" + CONTENT_TYPE + "\nhost:" + host + "\nx-tc-action:"
                + action.toLowerCase(Locale.ROOT) + "\n";
        var signedHeaders = "content-type;host;x-tc-action";
        var canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256(payload);
        var scope = date + "/" + SERVICE + "/tc3_request";
        var stringToSign = ALGORITHM + "\n" + timestamp + "\n" + scope + "\n" + sha256(canonicalRequest);
        var secretDate = hmac(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        var secretService = hmac(secretDate, SERVICE);
        var secretSigning = hmac(secretService, "tc3_request");
        var signature = HexFormat.of().formatHex(hmac(secretSigning, stringToSign));
        return ALGORITHM + " Credential=" + secretId + "/" + scope + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    static String contentType() {
        return CONTENT_TYPE;
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("HmacSHA256 不可用", error);
        }
    }
}