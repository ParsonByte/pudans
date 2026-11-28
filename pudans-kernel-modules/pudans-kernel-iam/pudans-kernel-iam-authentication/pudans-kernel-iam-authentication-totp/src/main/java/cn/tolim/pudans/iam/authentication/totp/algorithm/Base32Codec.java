package cn.tolim.pudans.iam.authentication.totp.algorithm;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

// RFC 4648 Base32 codec without padding, used by otpauth secret labels
public final class Base32Codec {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int[] LOOKUP = new int[128];

    static {
        for (int index = 0; index < LOOKUP.length; index++) {
            LOOKUP[index] = -1;
        }
        for (int index = 0; index < ALPHABET.length; index++) {
            LOOKUP[ALPHABET[index]] = index;
            LOOKUP[Character.toLowerCase(ALPHABET[index])] = index;
        }
    }

    private Base32Codec() {
    }

    // Encodes bytes into an upper-case Base32 string without padding
    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder encoded = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                encoded.append(ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            encoded.append(ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return encoded.toString();
    }

    // Decodes a Base32 string, ignoring padding characters and whitespace
    public static byte[] decode(String text) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        String normalized = text.trim().replace(" ", "").replace("=", "")
                .toUpperCase(Locale.ROOT);
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(normalized.length() * 5 / 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            int value = current < LOOKUP.length ? LOOKUP[current] : -1;
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character at index " + index);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                decoded.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return decoded.toByteArray();
    }
}