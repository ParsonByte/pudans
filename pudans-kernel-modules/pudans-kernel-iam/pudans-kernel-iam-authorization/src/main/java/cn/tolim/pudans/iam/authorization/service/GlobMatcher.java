package cn.tolim.pudans.iam.authorization.service;

import java.util.regex.Pattern;


public final class GlobMatcher {

    private GlobMatcher() {
    }

    // Checks whether a value matches a wildcard pattern
    // Parameter pattern: String, supports * and ? wildcards
    // Parameter value: String
    // Returns: boolean
    public static boolean matches(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }
        if ("*".equals(pattern)) {
            return true;
        }
        return Pattern.compile(toRegex(pattern), Pattern.DOTALL).matcher(value).matches();
    }

    // Converts a wildcard pattern to a regular expression
    // Parameter pattern: String
    // Returns: String
    static String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        for (char character : pattern.toCharArray()) {
            switch (character) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' ->
                        regex.append('\\').append(character);
                default -> regex.append(character);
            }
        }
        return regex.append('$').toString();
    }

    // Checks whether a pattern contains a wildcard character
    // Parameter pattern: String
    // Returns: boolean
    public static boolean isWildcard(String pattern) {
        return pattern != null && (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0);
    }
}