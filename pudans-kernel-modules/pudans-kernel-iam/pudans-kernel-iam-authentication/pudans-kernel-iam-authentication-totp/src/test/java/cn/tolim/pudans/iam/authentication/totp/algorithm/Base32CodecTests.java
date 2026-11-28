package cn.tolim.pudans.iam.authentication.totp.algorithm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base32CodecTests {

    @Test
    void encodesRfc4648WithoutPadding() {
        byte[] data = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Base32Codec.encode(data));
        assertEquals("MY", Base32Codec.encode("f".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("MZXQ", Base32Codec.encode("fo".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("MZXW6", Base32Codec.encode("foo".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void roundTripsRandomSecrets() {
        byte[] secret = new byte[32];
        for (int index = 0; index < secret.length; index++) {
            secret[index] = (byte) index;
        }
        assertArrayEquals(secret, Base32Codec.decode(Base32Codec.encode(secret)));
    }

    @Test
    void acceptsLowerCaseAndPadding() {
        assertEquals("foobar", new String(Base32Codec.decode("mzxw6ytboi======"),
                StandardCharsets.US_ASCII));
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("MZXW1"));
    }
}