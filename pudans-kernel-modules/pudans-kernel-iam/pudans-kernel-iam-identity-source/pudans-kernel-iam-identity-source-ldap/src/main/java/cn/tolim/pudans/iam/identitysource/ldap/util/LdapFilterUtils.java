package cn.tolim.pudans.iam.identitysource.ldap.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;


public final class LdapFilterUtils {

    public static final String WILDCARD = "*";

    private LdapFilterUtils() {
    }

    // Escape a value for use inside an LDAP filter per RFC 4515
    public static String escape(String value) {
        if (value == null) {
            return StringUtils.EMPTY;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            switch (character) {
                case '\\' -> builder.append("\\5c");
                case '*' -> builder.append("\\2a");
                // Parentheses must be escaped inside a filter assertion
                case '(' -> builder.append("\\28");
                case ')' -> builder.append("\\29");
                case '\u0000' -> builder.append("\\00");
                default -> builder.append(character);
            }
        }
        return builder.toString();
    }

    // Build an equality assertion
    public static String equality(String attribute, String value) {
        return "(" + attribute + "=" + escape(value) + ")";
    }

    // Build an attribute presence assertion
    public static String presence(String attribute) {
        return "(" + attribute + "=" + WILDCARD + ")";
    }

    // Combine filters with AND, ignoring blank parts
    public static String and(String... filters) {
        String parts = Arrays.stream(filters)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining());
        if (StringUtils.isBlank(parts)) {
            return "(objectClass=*)";
        }
        return "(&" + parts + ")";
    }

    // Combine filters with OR, ignoring blank parts
    public static String or(String... filters) {
        String parts = Arrays.stream(filters)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining());
        if (StringUtils.isBlank(parts)) {
            return "(objectClass=*)";
        }
        return "(|" + parts + ")";
    }

    // Wrap a filter body in parentheses when needed
    public static String normalize(String filter) {
        if (StringUtils.isBlank(filter)) {
            return "(objectClass=*)";
        }
        String trimmed = filter.trim();
        if (Objects.equals(trimmed.charAt(0), '(') && Objects.equals(trimmed.charAt(trimmed.length() - 1), ')')) {
            return trimmed;
        }
        return "(" + trimmed + ")";
    }
}