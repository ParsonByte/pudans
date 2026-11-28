package cn.tolim.pudans.iam.authentication.totp.algorithm;

import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

// Builds an otpauth:// enrollment URI compatible with common authenticator apps
public final class TotpUriFactory {

    private TotpUriFactory() {
    }

    // Builds the otpauth URI for a TOTP secret
    public static String build(String issuer, String accountName, String secretBase32,
                               TotpAlgorithm algorithm, int digits, int periodSeconds) {
        String safeIssuer = requireText(issuer, "issuer");
        String safeAccount = requireText(accountName, "accountName");
        String safeSecret = requireText(secretBase32, "secretBase32");
        String label = encode(safeIssuer) + ":" + encode(safeAccount);
        return "otpauth://totp/" + label
                + "?secret=" + safeSecret
                + "&issuer=" + encode(safeIssuer)
                + "&algorithm=" + algorithm.name()
                + "&digits=" + digits
                + "&period=" + periodSeconds;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TOTP " + name + " must not be blank");
        }
        return value;
    }

    // Percent-encodes all characters outside the RFC 3986 unreserved set
    static String encode(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(bytes.length * 3);
        for (byte current : bytes) {
            int code = current & 0xFF;
            if (isUnreserved(code)) {
                encoded.write(code);
            } else {
                encoded.write('%');
                encoded.write(Character.toUpperCase(Character.forDigit((code >> 4) & 0xF, 16)));
                encoded.write(Character.toUpperCase(Character.forDigit(code & 0xF, 16)));
            }
        }
        return encoded.toString(StandardCharsets.US_ASCII);
    }

    private static boolean isUnreserved(int code) {
        return (code >= 'A' && code <= 'Z')
                || (code >= 'a' && code <= 'z')
                || (code >= '0' && code <= '9')
                || code == '-' || code == '.' || code == '_' || code == '~';
    }
}