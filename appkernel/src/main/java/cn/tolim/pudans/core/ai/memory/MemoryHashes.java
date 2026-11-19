package cn.tolim.pudans.core.ai.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class MemoryHashes {

    private static final HexFormat HEX = HexFormat.of();

    private MemoryHashes() {}

    public static String sha256Hex(String value) {
        Objects.requireNonNull(value, "value");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
