package cn.tolim.pudans.core.ai.memory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record L3Persona(
        Long id,
        String teamId,
        String agentId,
        Map<String, Object> personaJson,
        String personaText,
        int sourceL1Count,
        int sourceL2Count,
        BigDecimal confidence,
        Instant lastRefreshedAt) {

    public L3Persona {
        requireText(teamId, "teamId");
        requireText(agentId, "agentId");
        personaJson = personaJson == null ? Map.of() : Map.copyOf(personaJson);
        Objects.requireNonNull(personaText, "personaText");
        if (sourceL1Count < 0 || sourceL2Count < 0) {
            throw new IllegalArgumentException("来源计数不能为负数");
        }
        Objects.requireNonNull(confidence, "confidence");
        if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence 必须在 0 到 1 之间");
        }
    }

    public L3Persona withId(Long id) {
        return new L3Persona(
                id,
                teamId,
                agentId,
                personaJson,
                personaText,
                sourceL1Count,
                sourceL2Count,
                confidence,
                lastRefreshedAt);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
