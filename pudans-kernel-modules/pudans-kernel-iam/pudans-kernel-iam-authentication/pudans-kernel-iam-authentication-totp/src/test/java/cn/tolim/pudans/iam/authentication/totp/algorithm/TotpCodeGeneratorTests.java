package cn.tolim.pudans.iam.authentication.totp.algorithm;

import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

// RFC 6238 Appendix B test vectors
class TotpCodeGeneratorTests {

    private static final byte[] SHA1_SECRET =
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SHA256_SECRET =
            "12345678901234567890123456789012".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SHA512_SECRET =
            "1234567890123456789012345678901234567890123456789012345678901234"
                    .getBytes(StandardCharsets.US_ASCII);

    @Test
    void generatesRfc6238Sha1Vectors() {
        assertEquals("94287082", code(SHA1_SECRET, TotpAlgorithm.SHA1, 59));
        assertEquals("07081804", code(SHA1_SECRET, TotpAlgorithm.SHA1, 1111111109L));
        assertEquals("14050471", code(SHA1_SECRET, TotpAlgorithm.SHA1, 1111111111L));
        assertEquals("89005924", code(SHA1_SECRET, TotpAlgorithm.SHA1, 1234567890L));
        assertEquals("69279037", code(SHA1_SECRET, TotpAlgorithm.SHA1, 2000000000L));
        assertEquals("65353130", code(SHA1_SECRET, TotpAlgorithm.SHA1, 20000000000L));
    }

    @Test
    void generatesRfc6238Sha256Vectors() {
        assertEquals("46119246", code(SHA256_SECRET, TotpAlgorithm.SHA256, 59));
        assertEquals("68084774", code(SHA256_SECRET, TotpAlgorithm.SHA256, 1111111109L));
        assertEquals("67062674", code(SHA256_SECRET, TotpAlgorithm.SHA256, 1111111111L));
        assertEquals("91819424", code(SHA256_SECRET, TotpAlgorithm.SHA256, 1234567890L));
        assertEquals("90698825", code(SHA256_SECRET, TotpAlgorithm.SHA256, 2000000000L));
        assertEquals("77737706", code(SHA256_SECRET, TotpAlgorithm.SHA256, 20000000000L));
    }

    @Test
    void generatesRfc6238Sha512Vectors() {
        assertEquals("90693936", code(SHA512_SECRET, TotpAlgorithm.SHA512, 59));
        assertEquals("25091201", code(SHA512_SECRET, TotpAlgorithm.SHA512, 1111111109L));
        assertEquals("99943326", code(SHA512_SECRET, TotpAlgorithm.SHA512, 1111111111L));
        assertEquals("93441116", code(SHA512_SECRET, TotpAlgorithm.SHA512, 1234567890L));
        assertEquals("38618901", code(SHA512_SECRET, TotpAlgorithm.SHA512, 2000000000L));
        assertEquals("47863826", code(SHA512_SECRET, TotpAlgorithm.SHA512, 20000000000L));
    }

    @Test
    void computesTimeStepForPeriod() {
        assertEquals(1L, TotpCodeGenerator.timeStep(java.time.Instant.ofEpochSecond(59), 30));
        assertEquals(2L, TotpCodeGenerator.timeStep(java.time.Instant.ofEpochSecond(60), 30));
        assertEquals(0L, TotpCodeGenerator.timeStep(java.time.Instant.ofEpochSecond(0), 30));
    }

    private static String code(byte[] secret, TotpAlgorithm algorithm, long epochSecond) {
        return TotpCodeGenerator.generate(secret, algorithm,
                TotpCodeGenerator.timeStep(java.time.Instant.ofEpochSecond(epochSecond), 30), 8);
    }
}