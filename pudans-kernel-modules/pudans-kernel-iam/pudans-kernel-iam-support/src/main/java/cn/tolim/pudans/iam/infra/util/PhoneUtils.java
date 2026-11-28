package cn.tolim.pudans.iam.infra.util;

public final class PhoneUtils {
    public static final String PLUS_SIGN = "+";
    public static final String PHONE_REGEXP = "^1[3-9]\\d{9}$";

    private PhoneUtils() {
    }

    public static String getPhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.replaceAll("[\\s-]", "");
        if (normalized.startsWith("+86")) {
            return normalized.substring(3);
        }
        if (normalized.startsWith("0086")) {
            return normalized.substring(4);
        }
        return normalized;
    }

    public static String getPhoneAreaCode(String phone) {
        String normalized = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (normalized.startsWith("+86") || normalized.startsWith("0086")) {
            return "86";
        }
        return normalized.startsWith("+") ? normalized.substring(1).replaceAll("\\D.*", "") : "";
    }

    public static boolean isPhoneValidate(String phone) {
        return phone != null && getPhoneNumber(phone).matches(PHONE_REGEXP);
    }
}
