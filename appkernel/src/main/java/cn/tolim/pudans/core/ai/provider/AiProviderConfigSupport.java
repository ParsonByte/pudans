package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.infra.exception.ApiException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public final class AiProviderConfigSupport {

    public static final String CODE_INVALID = "ai-provider-config-invalid";

    private AiProviderConfigSupport() {}

    public static void validate(AiProvider provider) {
        var spec = provider == null ? null : provider.getSpec();
        var baseUrl = spec == null ? null : spec.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw invalid("供应商未配置 Base URL。");
        }
        var trimmed = baseUrl.strip();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException error) {
            throw invalid("供应商 Base URL 不合法。", error);
        }
        var scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw invalid("供应商 Base URL 只允许 http 或 https。");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalid("供应商 Base URL 不能包含查询串或片段。");
        }
    }

    public static ApiException invalid(String reason) {
        return invalid(reason, null);
    }

    public static ApiException invalid(String reason, Throwable cause) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, reason, cause);
    }
}
