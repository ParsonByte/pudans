package cn.tolim.pudans.iam.authentication.totp.algorithm;

import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpCodeVerifierTests {

    private static final byte[] SECRET =
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final TotpCodeVerifier verifier = new TotpCodeVerifier();

    @Test
    void acceptsCurrentStep() {
        String code = currentCode(NOW);
        OptionalLong result = verifier.verify(SECRET, code, TotpAlgorithm.SHA1, 6, 30, 1, NOW);
        assertTrue(result.isPresent());
        assertEquals(TotpCodeGenerator.timeStep(NOW, 30), result.getAsLong());
    }

    @Test
    void acceptsPreviousAndNextStepsWithinWindow() {
        Instant previous = NOW.minusSeconds(30);
        Instant next = NOW.plusSeconds(30);
        assertTrue(verifier.verify(SECRET, currentCode(previous), TotpAlgorithm.SHA1, 6, 30, 1, NOW)
                .isPresent());
        assertTrue(verifier.verify(SECRET, currentCode(next), TotpAlgorithm.SHA1, 6, 30, 1, NOW)
                .isPresent());
    }

    @Test
    void rejectsStepsOutsideWindow() {
        String twoStepsBack = currentCode(NOW.minusSeconds(60));
        assertTrue(verifier.verify(SECRET, twoStepsBack, TotpAlgorithm.SHA1, 6, 30, 1, NOW)
                .isEmpty());
    }

    @Test
    void rejectsMalformedCodes() {
        assertTrue(verifier.verify(SECRET, "", TotpAlgorithm.SHA1, 6, 30, 1, NOW).isEmpty());
        assertTrue(verifier.verify(SECRET, "abcdef", TotpAlgorithm.SHA1, 6, 30, 1, NOW).isEmpty());
        assertTrue(verifier.verify(SECRET, "12345", TotpAlgorithm.SHA1, 6, 30, 1, NOW).isEmpty());
        assertTrue(verifier.verify(null, "123456", TotpAlgorithm.SHA1, 6, 30, 1, NOW).isEmpty());
    }

    private static String currentCode(Instant instant) {
        return TotpCodeGenerator.generate(SECRET, TotpAlgorithm.SHA1,
                TotpCodeGenerator.timeStep(instant, 30), 6);
    }
}