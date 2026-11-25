package cn.tolim.pudans.core.ai.protocol;

import java.util.Optional;

public final class AiProtocolProfiles {

    private AiProtocolProfiles() {}

    public static <T extends AiProtocolProfile> Optional<T> find(
            AiProtocolProfile profile, Class<T> type) {
        if (profile == null) {
            return Optional.empty();
        }
        if (!type.isInstance(profile)) {
            throw new IllegalArgumentException("AI 协议配置类型与协议不匹配。");
        }
        return Optional.of(type.cast(profile));
    }

    public static <T extends AiProtocolProfile> T resolve(
            AiProtocolProfile profile, Class<T> type, T fallback) {
        return find(profile, type).orElse(fallback);
    }
}
