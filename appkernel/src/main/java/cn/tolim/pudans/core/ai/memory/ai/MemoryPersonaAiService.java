package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.memory.JevGate;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.ai.memory.L3PipelineService;
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
public class MemoryPersonaAiService {

    private static final int MAX_OUTPUT_TOKENS = 2048;

    private static final double DEFAULT_CONFIDENCE = 0.7;

    private static final String SYSTEM_PROMPT = """
            你是团队 Agent 画像生成器，负责根据当前画像和变化场景合成长期画像。
            规则：
            1. personaJson 固定包含 background、preferences、work_style、expertise、current_focus、
               communication_notes、doctrine 字段，值可为字符串、数组或对象。
            2. personaText 使用中文，不超过 200 字，只描述稳定且可复用的团队画像。
            3. sourceL1Count 和 sourceL2Count 为非负整数，sourceL2Count 不得超过输入场景数量。
            4. confidence 为 0 到 1 之间的小数。
            5. 不要编造未在输入中出现的事实。
            只输出一个 JSON 对象，不要输出解释、Markdown 代码块或任何多余文本。
            JSON 结构：
            {"personaJson":{"background":"","preferences":[],"work_style":"","expertise":[],
              "current_focus":"","communication_notes":[],"doctrine":[]},
              "personaText":"画像正文","sourceL1Count":0,"sourceL2Count":0,"confidence":0.7}
            """;

    private final MemoryAiSupport support;

    public Mono<L3PipelineService.GeneratedPersona> generate(
            String systemPrompt,
            @Nullable L3Persona currentPersona,
            List<JevGate.ChangedScene> changedScenes) {
        var scenes = changedScenes == null
                ? List.<JevGate.ChangedScene>of()
                : changedScenes.stream().filter(Objects::nonNull).toList();
        return support.generate(
                        MemoryAiSupport.combine(systemPrompt, SYSTEM_PROMPT),
                        userPrompt(currentPersona, scenes),
                        MAX_OUTPUT_TOKENS)
                .map(result -> persona(result.content(), scenes.size()));
    }

    private L3PipelineService.GeneratedPersona persona(String content, int sceneCount) {
        var root = support.parseObject(content);
        var personaText = MemoryAiSupport.text(root, "personaText");
        if (!StringUtils.hasText(personaText)) {
            throw MemoryAiSupport.invalid("Persona 生成结果缺少 personaText。", null);
        }
        var sourceL1Count = Math.max(0, MemoryAiSupport.integer(root, "sourceL1Count", 0));
        var sourceL2Count = Math.max(0, Math.min(
                MemoryAiSupport.integer(root, "sourceL2Count", sceneCount), sceneCount));
        var confidence = BigDecimal.valueOf(MemoryAiSupport.confidence(root, DEFAULT_CONFIDENCE));
        return new L3PipelineService.GeneratedPersona(
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
