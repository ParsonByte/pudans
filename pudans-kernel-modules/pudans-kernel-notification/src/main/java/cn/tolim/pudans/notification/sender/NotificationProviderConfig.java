package cn.tolim.pudans.notification.sender;

import cn.tolim.pudans.notification.exception.NotificationValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// Provider settings read from the encrypted configuration document.
public final class NotificationProviderConfig {

    private static final Set<String> SECRET_KEYS = Set.of(ProviderConfigKeys.PASSWORD,
            ProviderConfigKeys.ACCESS_KEY_SECRET, ProviderConfigKeys.SECRET_KEY,
            ProviderConfigKeys.APP_SECRET, ProviderConfigKeys.WEBHOOK_URL);

    private static final String MASK = "******";

    private final Map<String, String> values;

    public NotificationProviderConfig(Map<String, String> values) {
        this.values = values == null ? Map.of() : new LinkedHashMap<>(values);
    }

    public String get(String key) {
        String value = values.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String require(String key) {
        String value = get(key);
        if (value == null) {
            throw new NotificationValidationException(
                    "Missing required provider configuration: " + key);
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new NotificationValidationException(
                    "Provider configuration " + key + " must be a number");
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public Map<String, String> asMap() {
        return new LinkedHashMap<>(values);
    }

    public Map<String, String> masked() {
        Map<String, String> masked = new LinkedHashMap<>();
        values.forEach((key, value) -> masked.put(key,
                SECRET_KEYS.contains(key) && value != null && !value.isBlank() ? MASK : value));
        return masked;
    }

    public static boolean isSecretKey(String key) {
        return SECRET_KEYS.contains(key);
    }

    public static String maskValue(String key, String value) {
        if (SECRET_KEYS.contains(key) && value != null && !value.isBlank()) {
            return MASK;
        }
        return value;
    }
}