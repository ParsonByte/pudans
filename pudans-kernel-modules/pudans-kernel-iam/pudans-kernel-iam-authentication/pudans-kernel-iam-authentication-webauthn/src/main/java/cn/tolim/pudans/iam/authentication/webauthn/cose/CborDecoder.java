package cn.tolim.pudans.iam.authentication.webauthn.cose;

import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Minimal definite-length CBOR decoder sufficient for WebAuthn structures
public final class CborDecoder {

    private CborDecoder() {
    }

    // Decodes exactly one CBOR item and rejects trailing bytes
    public static Object decode(byte[] data) {
        Decoded decoded = decodeFirst(data);
        if (decoded.consumed() != data.length) {
            throw new WebAuthnVerificationException("Unexpected trailing CBOR bytes");
        }
        return decoded.value();
    }

    // Decodes one CBOR item and reports how many bytes it occupies
    public static Decoded decodeFirst(byte[] data) {
        if (data == null) {
            throw new WebAuthnVerificationException("CBOR data must not be null");
        }
        int[] offset = {0};
        Object value = read(data, offset);
        return new Decoded(value, offset[0]);
    }

    private static Object read(byte[] data, int[] offset) {
        int initial = readByte(data, offset);
        int major = initial >> 5;
        int additional = initial & 0x1F;
        return switch (major) {
            case 0 -> readArgument(data, offset, additional);
            case 1 -> -1L - readArgument(data, offset, additional);
            case 2 -> readBytes(data, offset, additional);
            case 3 -> readText(data, offset, additional);
            case 4 -> readArray(data, offset, additional);
            case 5 -> readMap(data, offset, additional);
            case 6 -> read(data, offset);
            case 7 -> readSimple(data, offset, additional);
            default -> throw new WebAuthnVerificationException("Unsupported CBOR major type");
        };
    }

    private static long readArgument(byte[] data, int[] offset, int additional) {
        return switch (additional) {
            case 24 -> readByte(data, offset);
            case 25 -> readUnsigned(data, offset, 2);
            case 26 -> readUnsigned(data, offset, 4);
            case 27 -> readUnsigned(data, offset, 8);
            default -> {
                if (additional < 24) {
                    yield additional;
                }
                throw new WebAuthnVerificationException("Indefinite CBOR lengths are not supported");
            }
        };
    }

    private static byte[] readBytes(byte[] data, int[] offset, int additional) {
        int length = toLength(readArgument(data, offset, additional));
        requireAvailable(data, offset[0], length);
        byte[] value = new byte[length];
        System.arraycopy(data, offset[0], value, 0, length);
        offset[0] += length;
        return value;
    }

    private static String readText(byte[] data, int[] offset, int additional) {
        byte[] bytes = readBytes(data, offset, additional);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<Object> readArray(byte[] data, int[] offset, int additional) {
        int length = toLength(readArgument(data, offset, additional));
        List<Object> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(read(data, offset));
        }
        return values;
    }

    private static Map<Object, Object> readMap(byte[] data, int[] offset, int additional) {
        int length = toLength(readArgument(data, offset, additional));
        Map<Object, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < length; index++) {
            Object key = read(data, offset);
            Object value = read(data, offset);
            values.put(key, value);
        }
        return values;
    }

    private static Object readSimple(byte[] data, int[] offset, int additional) {
        return switch (additional) {
            case 20 -> Boolean.FALSE;
            case 21 -> Boolean.TRUE;
            case 22 -> null;
            case 26 -> Float.intBitsToFloat((int) readUnsigned(data, offset, 4));
            case 27 -> Double.longBitsToDouble(readUnsigned(data, offset, 8));
            default -> throw new WebAuthnVerificationException("Unsupported CBOR simple value");
        };
    }

    private static int readByte(byte[] data, int[] offset) {
        requireAvailable(data, offset[0], 1);
        return data[offset[0]++] & 0xFF;
    }

    private static long readUnsigned(byte[] data, int[] offset, int length) {
        requireAvailable(data, offset[0], length);
        long value = 0;
        for (int index = 0; index < length; index++) {
            value = (value << 8) | (data[offset[0]++] & 0xFFL);
        }
        if (value < 0) {
            throw new WebAuthnVerificationException("CBOR integer exceeds the supported range");
        }
        return value;
    }

    private static int toLength(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new WebAuthnVerificationException("CBOR length is out of range");
        }
        return (int) value;
    }

    private static void requireAvailable(byte[] data, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new WebAuthnVerificationException("CBOR data is truncated");
        }
    }

    // One decoded item together with its encoded length
    public record Decoded(Object value, int consumed) {
    }
}