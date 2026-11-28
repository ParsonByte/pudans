package cn.tolim.pudans.iam.authorization.service;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;


public final class ConditionMatcher {

    private static final String CONDITION_MFA = "mfa";
    private static final String CONDITION_IP = "ip";
    private static final String CONDITION_ORG_ID = "orgId";
    private static final String CONDITION_ORGANIZATION_ID = "organizationId";
    private static final String CONDITION_TIME = "time";
    private static final String CONDITION_TAGS = "tags";

    private ConditionMatcher() {
    }

    // Checks whether every declared condition is satisfied, conditions fail closed
    // Parameter condition: Map<String, Object> declared conditions
    // Parameter orgId: String request organization ID
    // Parameter context: Map<String, Object> request evaluation context
    // Returns: boolean
    public static boolean matches(Map<String, Object> condition, String orgId, Map<String, Object> context) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        Map<String, Object> values = context == null ? Map.of() : context;
        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            if (!matchesCondition(entry.getKey(), entry.getValue(), orgId, values)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesCondition(String key, Object expected, String orgId,
                                            Map<String, Object> context) {
        if (key == null) {
            return false;
        }
        if (expected == null) {
            return true;
        }
        return switch (key) {
            case CONDITION_MFA -> truthy(expected) == truthy(contextValue(context, CONDITION_MFA));
            case CONDITION_IP -> ipAllowed(contextValue(context, CONDITION_IP), expected);
            case CONDITION_ORG_ID, CONDITION_ORGANIZATION_ID -> {
                Object actual = orgId != null ? orgId : contextValue(context, key);
                yield matchesValue(actual, expected);
            }
            case CONDITION_TIME -> matchesTime(contextValue(context, CONDITION_TIME), expected);
            case CONDITION_TAGS -> matchesTags(contextValue(context, CONDITION_TAGS), expected);
            default -> matchesValue(contextValue(context, key), expected);
        };
    }

    private static Object contextValue(Map<String, Object> context, String key) {
        Object value = context.get(key);
        if (value == null && CONDITION_IP.equals(key)) {
            value = context.get("clientIp");
        }
        if (value == null && CONDITION_MFA.equals(key)) {
            value = context.get("mfaVerified");
        }
        return value;
    }

    // Checks whether an actual value equals the expected scalar, list member or map entry
    // Parameter actual: Object
    // Parameter expected: Object
    // Returns: boolean
    private static boolean matchesValue(Object actual, Object expected) {
        if (actual == null) {
            return false;
        }
        if (expected instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (matchesValue(actual, element)) {
                    return true;
                }
            }
            return false;
        }
        if (expected instanceof Map<?, ?> map) {
            if (actual instanceof Map<?, ?> actualMap) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object actualValue = actualMap.get(entry.getKey());
                    if (actualValue == null || !matchesValue(actualValue, entry.getValue())) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
        if (actual instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (matchesValue(element, expected)) {
                    return true;
                }
            }
            return false;
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text)
                || "yes".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text);
    }

    // Checks whether the actual IP is contained in the expected IP or CIDR list
    // Parameter actual: Object actual IP address
    // Parameter expected: Object expected IP, CIDR or a collection of them
    // Returns: boolean
    private static boolean ipAllowed(Object actual, Object expected) {
        if (actual == null) {
            return false;
        }
        if (expected instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (ipAllowed(actual, element)) {
                    return true;
                }
            }
            return false;
        }
        String address = String.valueOf(actual).trim();
        String rule = String.valueOf(expected).trim();
        if (address.isEmpty() || rule.isEmpty()) {
            return false;
        }
        try {
            InetAddress candidate = InetAddress.getByName(address);
            if (!rule.contains("/")) {
                return candidate.equals(InetAddress.getByName(rule));
            }
            String[] parts = rule.split("/", 2);
            int prefix = Integer.parseInt(parts[1]);
            InetAddress network = InetAddress.getByName(parts[0]);
            byte[] candidateBytes = candidate.getAddress();
            byte[] networkBytes = network.getAddress();
            if (candidateBytes.length != networkBytes.length
                    || prefix < 0 || prefix > candidateBytes.length * 8) {
                return false;
            }
            int fullBytes = prefix / 8;
            for (int index = 0; index < fullBytes; index++) {
                if (candidateBytes[index] != networkBytes[index]) {
                    return false;
                }
            }
            int remainingBits = prefix % 8;
            if (remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits);
                return (candidateBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    // Checks a time window condition, the value may be a scalar or a map with after and before
    // Parameter actual: Object actual time
    // Parameter expected: Object expected time or time window
    // Returns: boolean
    private static boolean matchesTime(Object actual, Object expected) {
        if (!(expected instanceof Map<?, ?> window)) {
            return matchesValue(actual, expected);
        }
        LocalDateTime time = parseTime(actual);
        if (time == null) {
            return false;
        }
        Object after = window.get("after");
        Object before = window.get("before");
        if (after != null) {
            LocalDateTime afterTime = parseTime(after);
            if (afterTime == null || time.isBefore(afterTime)) {
                return false;
            }
        }
        if (before != null) {
            LocalDateTime beforeTime = parseTime(before);
            if (beforeTime == null || time.isAfter(beforeTime)) {
                return false;
            }
        }
        return true;
    }

    private static LocalDateTime parseTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (Exception ignored) {
            // Try the next supported format
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception ignored) {
            // Try the next supported format
        }
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(text)),
                    java.time.ZoneId.systemDefault());
        } catch (Exception ignored) {
            return null;
        }
    }

    // Checks whether the actual tag map contains every expected tag entry
    // Parameter actual: Object actual tags
    // Parameter expected: Object expected tags
    // Returns: boolean
    private static boolean matchesTags(Object actual, Object expected) {
        if (!(expected instanceof Map<?, ?> expectedTags) || !(actual instanceof Map<?, ?> actualTags)) {
            return false;
        }
        for (Map.Entry<?, ?> entry : expectedTags.entrySet()) {
            Object actualValue = actualTags.get(entry.getKey());
            if (actualValue == null || !matchesValue(actualValue, entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}