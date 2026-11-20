package cn.tolim.pudans.core.ai.protocol.a2a;

import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record A2aPart(String kind, @Nullable String text) {

    private static final String KIND_TEXT = "text";

    public static A2aPart ofText(String text) {
        return new A2aPart(KIND_TEXT, text);
    }

    public boolean hasText() {
        return KIND_TEXT.equals(kind) && StringUtils.hasText(text);
    }
}