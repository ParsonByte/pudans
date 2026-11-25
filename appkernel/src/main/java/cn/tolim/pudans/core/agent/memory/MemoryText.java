package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.infra.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

public enum MemoryText {
    ;

    public static final Set<String> PERSONAL_LAYERS = Set.of("L1", "L2", "L3");

    public static final Set<String> MEMORY_TYPES = Set.of(
            "intent", "preference", "fact", "project", "subject", "conversation", "creation");

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");

    private static final Pattern IDENTITY_NUMBER = Pattern.compile("\\b\\d{17}[\\dXx]\\b");

    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(api[_-]?key|secret|token|password|authorization)\\s*[:=]\\s*\\S+");

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?|ftp)://\\S+");

    public static String required(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value.trim();
    }

    public static String normalized(String value) {
        return value == null
                ? ""
                : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String contentHash(String content) {
        return sha256(normalized(content));
    }

    public static String queryHash(String query) {
        return sha256(normalized(query));
    }

    public static String digest(List<String> values) {
        return sha256(String.join("|", values));
    }

    public static long simhash(String content) {
        var tokens = Arrays.stream(normalized(content).split("\\s+"))
                .filter(token -> !token.isBlank())
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            return 0L;
        }
        var weights = new int[64];
        for (var token : tokens) {
            var hash = fnv1a64(token);
            for (var bit = 0; bit < 64; bit++) {
                weights[bit] += ((hash >>> bit) & 1L) == 1L ? 1 : -1;
            }
        }
        var value = 0L;
        for (var bit = 0; bit < 64; bit++) {
            if (weights[bit] >= 0) {
                value |= 1L << bit;
            }
        }
        return value;
    }

    public static int estimatedTokens(String value) {
        return Math.max(1, (value == null ? 0 : value.length() + 3) / 4);
    }

    public static double lexicalScore(String query, String content, String summary) {
        var tokens = Arrays.stream(normalized(query).split("\\s+"))
                .filter(token -> token.length() > 1)
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            return 0;
        }
        var text = normalized(content) + " " + normalized(summary);
        var matched = tokens.stream().filter(text::contains).count();
        return (double) matched / tokens.size();
    }

    public static double freshness(Instant updatedAt, Instant now) {
        if (updatedAt == null || now == null || updatedAt.isAfter(now)) {
            return 1;
        }
        var age = Duration.between(updatedAt, now).toDays();
        return Math.exp(-Math.max(0, age) / 90.0);
    }

    public static boolean containsPii(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return EMAIL.matcher(value).find()
                || PHONE.matcher(value).find()
                || IDENTITY_NUMBER.matcher(value).find()
                || CREDENTIAL.matcher(value).find()
                || URL.matcher(value).find();
    }

    public static String canonicalKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var key = normalized(value).replace(' ', '-');
        return key.length() <= 255 ? key : key.substring(0, 255);
    }

    public static String joinDistinct(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static long fnv1a64(String value) {
        var hash = 0xcbf29ce484222325L;
        for (var index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
