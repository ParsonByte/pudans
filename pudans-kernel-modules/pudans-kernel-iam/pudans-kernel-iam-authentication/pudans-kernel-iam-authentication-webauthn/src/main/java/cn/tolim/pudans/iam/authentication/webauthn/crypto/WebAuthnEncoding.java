package cn.tolim.pudans.iam.authentication.webauthn.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// Base64URL helpers for WebAuthn binary fields
public final class WebAuthnEncoding {

    private WebAuthnEncoding() {
    }

    // Encodes bytes as unpadded Base64URL
    public static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    // Decodes padded or unpadded Base64URL, also tolerating standard Base64
    public static byte[] decodeBase64Url(String text) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        String normalized = text.trim().replace('+', '-').replace('/', '_');
        int remainder = normalized.length() % 4;
        if (remainder == 2) {
            normalized = normalized + "==";
        } else if (remainder == 3) {
            normalized = normalized + "=";
        } else if (remainder == 1) {
            throw new IllegalArgumentException("Invalid Base64URL length");
        }
        return Base64.getUrlDecoder().decode(normalized);
    }

    // Encodes a UTF-8 string as Base64URL bytes
    public static String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    // Decodes Base64URL bytes as a UTF-8 string
    public static String decodeBase64UrlToString(String text) {
        return new String(decodeBase64Url(text), StandardCharsets.UTF_8);
    }
}