package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.prompt.MemoryPrompts;
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
public class JevClassification {

    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final MemoryAiSupport support;

    public Mono<JevChangeType> classify(String currentPersona, JevGate.ChangedScene scene) {
        Objects.requireNonNull(scene, "scene");
        return support.generate(MemoryPrompts.JEV, userPrompt(currentPersona, scene), MAX_OUTPUT_TOKENS)
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
