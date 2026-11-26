package cn.tolim.pudans.core.ai.provider;

import org.springframework.util.StringUtils;

public record AiTranscriptionOptions(
        String model, String language, String responseFormat, String timestampLevel) {

    public AiTranscriptionOptions(String model, String language, String responseFormat) {
        this(model, language, responseFormat, null);
    }

    public String modelOrNull() {
        return StringUtils.hasText(model) ? model.trim() : null;
    }

    public String languageOrNull() {
        return StringUtils.hasText(language) ? language.trim() : null;
    }

    public String responseFormatOrNull() {
        return StringUtils.hasText(responseFormat) ? responseFormat.trim() : null;
    }

    public String timestampLevelOrNull() {
        return StringUtils.hasText(timestampLevel) ? timestampLevel.trim() : null;
    }
}
