package cn.tolim.pudans.communal.log;

// Masks credentials and personal identifiers before they reach logs or audits.
public final class SensitiveDataMasker {

    private static final String MASK = "****";

    private SensitiveDataMasker() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return maskValue(email, 1, 0);
        }
        String localPart = email.substring(0, atIndex);
        String visibleLocalPart = localPart.length() <= 1 ? "" : localPart.substring(0, 1);
        return visibleLocalPart + MASK + email.substring(atIndex);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return MASK;
        }
        return digits.substring(0, 3) + MASK + digits.substring(digits.length() - 4);
    }

    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return secret;
        }
        return MASK;
    }

    public static String maskAuthorization(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return authorizationHeader;
        }
        int spaceIndex = authorizationHeader.indexOf(' ');
        if (spaceIndex <= 0) {
            return MASK;
        }
        return authorizationHeader.substring(0, spaceIndex) + " " + MASK;
    }

    public static String maskValue(String value, int keepPrefix, int keepSuffix) {
        if (value == null) {
            return null;
        }
        if (keepPrefix < 0 || keepSuffix < 0) {
            throw new IllegalArgumentException("keepPrefix and keepSuffix must not be negative");
        }
        if (value.length() <= keepPrefix + keepSuffix) {
            return MASK;
        }
        return value.substring(0, keepPrefix) + MASK + value.substring(value.length() - keepSuffix);
    }
}