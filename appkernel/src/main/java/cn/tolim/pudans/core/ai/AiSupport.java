package cn.tolim.pudans.core.ai;

import cn.tolim.pudans.infra.exception.ApiException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public enum AiSupport {
    ;

    public static final String ACTIVE = "active";

    public static final String DISABLED = "disabled";

    public static final String RELEASED = "released";

    public static final String SETTLED = "settled";

    public static final String RESERVED = "reserved";

    public static final Set<String> CAPABILITIES = Set.of(
            "chat",
            "image",
            "video",
            "audio",
            "embedding",
            "rerank",
            "3d",
            "transcription",
            "memory_extraction");

    public static final Duration FIVE_HOUR_WINDOW = Duration.ofHours(5);

    public static final Duration WEEKLY_WINDOW = Duration.ofDays(7);

    public static String requiredText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value.trim();
    }

    public static String normalizeStatus(String value, String defaultStatus) {
        var status = StringUtils.hasText(value) ? value.trim().toLowerCase() : defaultStatus;
        if (status.length() > 32) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-status-invalid",
                    "AI 配置状态长度不能超过 32 个字符。");
        }
        return status;
    }

    public static String normalizeEnabledStatus(String value, String defaultStatus) {
        var status = StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : defaultStatus;
        if (!ACTIVE.equals(status) && !DISABLED.equals(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-status-invalid",
                    "配额状态必须为 active 或 disabled。");
        }
        return status;
    }

    public static String normalizeCapability(String value) {
        var capability = requiredText(
                        value, "ai-model-capability-invalid", "模型能力不能为空。")
                .toLowerCase();
        if (!CAPABILITIES.contains(capability)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-model-capability-invalid",
                    "不支持的模型能力。");
        }
        return capability;
    }

    public static BigDecimal nonNegative(
            BigDecimal value, BigDecimal defaultValue, String code, String message) {
        var resolved = value == null ? defaultValue : value;
        if (resolved == null || resolved.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return resolved;
    }

    public static int positive(int value, String code, String message) {
        if (value <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value;
    }

    public static int nonNegative(int value, String code, String message) {
        if (value < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value;
    }

    public static Instant now() {
        return Instant.now();
    }
}
