package cn.tolim.pudans.iam.authentication.totp.algorithm;

import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpUriFactoryTests {

    @Test
    void buildsStandardOtpauthUri() {
        String uri = TotpUriFactory.build("PUDANS", "alice@example.com", "JBSWY3DPEHPK3PXP",
                TotpAlgorithm.SHA1, 6, 30);
        assertTrue(uri.startsWith("otpauth://totp/PUDANS:alice%40example.com?"));
        assertTrue(uri.contains("secret=JBSWY3DPEHPK3PXP"));
        assertTrue(uri.contains("issuer=PUDANS"));
        assertTrue(uri.contains("algorithm=SHA1"));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
    }

    @Test
    void percentEncodesSpecialCharacters() {
        String uri = TotpUriFactory.build("ACME 云", "用户 A", "ABC", TotpAlgorithm.SHA256, 8, 60);
        assertTrue(uri.contains("ACME%20%E4%BA%91:%E7%94%A8%E6%88%B7%20A"));
        assertTrue(uri.contains("issuer=ACME%20%E4%BA%91"));
    }

    @Test
    void rejectsBlankIssuer() {
        assertTrue(assertThrowsIllegal(() -> TotpUriFactory.build(" ", "alice", "ABC",
                TotpAlgorithm.SHA1, 6, 30)));
    }

    private static boolean assertThrowsIllegal(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}