package cn.tolim.pudans.iam.authentication.totp.algorithm;

import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

// Verifies TOTP codes within a bounded time-step window
public class TotpCodeVerifier {

    private static final int MAX_WINDOW = 10;

    // Returns the matched time step when the code is valid
    public OptionalLong verify(byte[] secret, String code, TotpAlgorithm algorithm, int digits,
                               int periodSeconds, int window, Instant at) {
        if (secret == null || secret.length == 0 || code == null) {
            return OptionalLong.empty();
        }
        if (window < 0 || window > MAX_WINDOW) {
            throw new IllegalArgumentException("TOTP window must be between 0 and 10");
        }
        String candidate = code.trim();
        if (candidate.length() != digits || !isNumeric(candidate)) {
            return OptionalLong.empty();
        }
        long currentStep = TotpCodeGenerator.timeStep(at, periodSeconds);
        byte[] candidateBytes = candidate.getBytes(StandardCharsets.US_ASCII);
        for (int offset = -window; offset <= window; offset++) {
            long step = currentStep + offset;
            if (step < 0) {
                continue;
            }
            String expected = TotpCodeGenerator.generate(secret, algorithm, step, digits);
            byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expectedBytes, candidateBytes)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    private static boolean isNumeric(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < '0' || current > '9') {
                return false;
            }
        }
        return true;
    }
}