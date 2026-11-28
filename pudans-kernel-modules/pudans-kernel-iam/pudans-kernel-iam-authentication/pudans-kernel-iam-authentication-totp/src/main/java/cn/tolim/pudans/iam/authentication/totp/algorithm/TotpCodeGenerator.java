package cn.tolim.pudans.iam.authentication.totp.algorithm;

import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.time.Instant;

// RFC 4226 HOTP and RFC 6238 TOTP code generation
public final class TotpCodeGenerator {

    private static final int[] POWERS_OF_TEN = {
            1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000,
            100_000_000, 1_000_000_000};
    private static final int MIN_DIGITS = 6;
    private static final int MAX_DIGITS = 10;

    private TotpCodeGenerator() {
    }

    // Computes the time step index for the given instant
    public static long timeStep(Instant instant, int periodSeconds) {
        if (periodSeconds <= 0) {
            throw new IllegalArgumentException("TOTP period must be positive");
        }
        long seconds = instant.getEpochSecond();
        long step = Math.floorDiv(seconds, periodSeconds);
        return Math.max(step, 0L);
    }

    // Generates a zero-padded numeric TOTP code for one time step
    public static String generate(byte[] secret, TotpAlgorithm algorithm, long timeStep, int digits) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("TOTP secret must not be empty");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("TOTP algorithm must not be null");
        }
        if (digits < MIN_DIGITS || digits > MAX_DIGITS) {
            throw new IllegalArgumentException("TOTP digits must be between 6 and 10");
        }
        byte[] counter = new byte[8];
        long remaining = timeStep;
        for (int index = 7; index >= 0; index--) {
            counter[index] = (byte) (remaining & 0xFF);
            remaining >>>= 8;
        }
        byte[] hash = hmac(secret, algorithm, counter);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int code = binary % POWERS_OF_TEN[digits];
        return leftPad(code, digits);
    }

    private static byte[] hmac(byte[] secret, TotpAlgorithm algorithm, byte[] counter) {
        try {
            Mac mac = Mac.getInstance(algorithm.getJcaName());
            mac.init(new SecretKeySpec(secret, algorithm.getJcaName()));
            return mac.doFinal(counter);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP HMAC computation failed", exception);
        }
    }

    private static String leftPad(int code, int digits) {
        String value = Integer.toString(code);
        StringBuilder padded = new StringBuilder(digits);
        for (int index = value.length(); index < digits; index++) {
            padded.append('0');
        }
        return padded.append(value).toString();
    }
}