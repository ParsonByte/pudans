package cn.tolim.pudans.core.infra.audit;

import java.security.SecureRandom;
import java.time.Instant;


public final class AuditIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AuditIdGenerator() {}

    public static String next() {
        var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        var millis = Instant.now().toEpochMilli();
        bytes[0] = (byte) (millis >>> 40);
        bytes[1] = (byte) (millis >>> 32);
        bytes[2] = (byte) (millis >>> 24);
        bytes[3] = (byte) (millis >>> 16);
        bytes[4] = (byte) (millis >>> 8);
        bytes[5] = (byte) millis;

        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x70);

        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        return format(bytes);
    }

    private static String format(byte[] bytes) {
        var sb = new StringBuilder(36);
        for (int i = 0; i < bytes.length; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                sb.append('-');
            }
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }
}
