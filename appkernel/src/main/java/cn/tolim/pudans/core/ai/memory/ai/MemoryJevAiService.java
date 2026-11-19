package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.memory.JevChangeType;
import cn.tolim.pudans.core.ai.memory.JevGate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MemoryJevAiService {

    private static final int MAX_OUTPUT_TOKENS = 1024;

    private static final String SYSTEM_PROMPT = """
            你是记忆变化类型判定器（JEV），负责比较当前画像与变化场景。
            规则：
            1. 只输出一个 changeType 字段，取值必须是 CONFLICT、BOUNDARY_CHANGE、NEW_RULE、
               REINFORCEMENT、NONE 之一。
            2. CONFLICT：变化与当前画像断言的事实矛盾或替换。
            3. BOUNDARY_CHANGE：规则适用范围发生持久变化。
            4. NEW_RULE：新增画像级规则，而非既有规则适用范围变化。
            5. REINFORCEMENT：仅支持或复述当前画像。
            6. NONE：没有持久画像级信息。
            只输出一个 JSON 对象，不要输出解释、Markdown 代码块或任何多余文本。
            JSON 结构：
            {"changeType":"CONFLICT|BOUNDARY_CHANGE|NEW_RULE|REINFORCEMENT|NONE"}
            """;

    private final MemoryAiSupport support;

    public Mono<JevChangeType> classify(String currentPersona, JevGate.ChangedScene scene) {
        Objects.requireNonNull(scene, "scene");
        return support.generate(SYSTEM_PROMPT, userPrompt(currentPersona, scene), MAX_OUTPUT_TOKENS)
                .map(result -> changeType(result.content()));
    }

    private JevChangeType changeType(String content) {
        try {
            var root = support.parseObject(content);
            var value = MemoryAiSupport.text(root, "changeType");
            return value == null ? JevChangeType.NONE : JevChangeType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException error) {
            return JevChangeType.NONE;
        }
    }

    private String userPrompt(String currentPersona, JevGate.ChangedScene scene) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("currentPersona", currentPersona == null ? "" : currentPersona);
        payload.put("changedScene", Map.of(
                "sceneKey", scene.sceneKey(),
                "summary", scene.summary(),
                "content", scene.content()));
        return "当前画像与变化场景如下：\n" + support.write(payload);
    }
}
