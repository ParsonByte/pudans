package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.prompt.MemoryPrompts;
import cn.tolim.pudans.core.ai.memory.JevGate;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.ai.memory.L3Pipeline;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PersonaProfiling {

    private static final int MAX_OUTPUT_TOKENS = 2048;

    private static final double DEFAULT_CONFIDENCE = 0.7;

    private final MemoryAiSupport support;

    public Mono<L3Pipeline.GeneratedPersona> generate(
            String systemPrompt,
            @Nullable L3Persona currentPersona,
            List<JevGate.ChangedScene> changedScenes) {
        var scenes = changedScenes == null
                ? List.<JevGate.ChangedScene>of()
                : changedScenes.stream().filter(Objects::nonNull).toList();
        return support.generate(
                        MemoryAiSupport.combine(systemPrompt, MemoryPrompts.PERSONA),
                        userPrompt(currentPersona, scenes),
                        MAX_OUTPUT_TOKENS)
                .map(result -> persona(result.content(), scenes.size()));
    }

    private L3Pipeline.GeneratedPersona persona(String content, int sceneCount) {
        var root = support.parseObject(content);
        var personaText = MemoryAiSupport.text(root, "personaText");
        if (!StringUtils.hasText(personaText)) {
            throw MemoryAiSupport.invalid("Persona 生成结果缺少 personaText。", null);
        }
        var sourceL1Count = Math.max(0, MemoryAiSupport.integer(root, "sourceL1Count", 0));
        var sourceL2Count = Math.max(0, Math.min(
                MemoryAiSupport.integer(root, "sourceL2Count", sceneCount), sceneCount));
        var confidence = BigDecimal.valueOf(MemoryAiSupport.confidence(root, DEFAULT_CONFIDENCE));
        return new L3Pipeline.GeneratedPersona(
                support.map(root.get("personaJson")),
                personaText,
                sourceL1Count,
                sourceL2Count,
                confidence);
    }

    private String userPrompt(
            @Nullable L3Persona currentPersona, List<JevGate.ChangedScene> changedScenes) {
        var scenes = new ArrayList<Map<String, Object>>(changedScenes.size());
        for (var scene : changedScenes) {
            scenes.add(Map.of(
                    "sceneKey", scene.sceneKey(),
                    "summary", scene.summary(),
                    "content", scene.content()));
        }
        var payload = new LinkedHashMap<String, Object>();
        if (currentPersona != null) {
            payload.put("currentPersona", Map.of(
                    "personaText", currentPersona.personaText(),
                    "personaJson", currentPersona.personaJson()));
        }
        payload.put("changedScenes", scenes);
        return "当前画像与变化场景如下：\n" + support.write(payload);
    }
}
