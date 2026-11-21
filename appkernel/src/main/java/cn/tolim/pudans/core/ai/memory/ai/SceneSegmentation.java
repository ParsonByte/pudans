package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.prompt.MemoryPrompts;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2Pipeline;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SceneSegmentation {

    private static final int MAX_OUTPUT_TOKENS = 2048;

    private static final double DEFAULT_CONFIDENCE = 0.7;

    private final MemoryAiSupport support;

    public Mono<List<L2Pipeline.ExtractedScene>> extract(
            String systemPrompt, List<L1MemoryRecord> newL1, List<L2SceneBlock> existingScenes) {
        var pending = newL1 == null
                ? List.<L1MemoryRecord>of()
                : newL1.stream().filter(record -> record != null && record.id() != null).toList();
        if (pending.isEmpty()) {
            return Mono.just(List.of());
        }
        var scenes = existingScenes == null
                ? List.<L2SceneBlock>of()
                : existingScenes.stream().filter(Objects::nonNull).toList();
        return support.generate(
                        MemoryAiSupport.combine(systemPrompt, MemoryPrompts.SCENE),
                        userPrompt(pending, scenes),
                        MAX_OUTPUT_TOKENS)
                .map(result -> extracted(result.content(), pending, scenes));
    }

    private List<L2Pipeline.ExtractedScene> extracted(
            String content, List<L1MemoryRecord> newL1, List<L2SceneBlock> existingScenes) {
        var knownIds = knownIds(newL1, existingScenes);
        var root = support.parseObject(content);
        var scenesNode = root.get("scenes");
        if (scenesNode == null || !scenesNode.isArray()) {
            throw MemoryAiSupport.invalid("场景抽取结果缺少 scenes 数组。", null);
        }
        var scenes = new ArrayList<L2Pipeline.ExtractedScene>();
        for (var node : scenesNode) {
            var scene = scene(node, knownIds);
            if (scene != null) {
                scenes.add(scene);
            }
        }
        return List.copyOf(scenes);
    }

    private static L2Pipeline.ExtractedScene scene(JsonNode node, Set<Long> knownIds) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var sceneKey = MemoryAiSupport.text(node, "sceneKey");
        var title = MemoryAiSupport.text(node, "title");
        var summary = MemoryAiSupport.text(node, "summary");
        if (!StringUtils.hasText(sceneKey)
                || !StringUtils.hasText(title)
                || !StringUtils.hasText(summary)) {
            return null;
        }
        var l1Ids = MemoryAiSupport.longArray(node, "l1Ids").stream()
                .filter(knownIds::contains)
                .distinct()
                .toList();
        if (l1Ids.isEmpty()) {
            return null;
        }
        var confidence = BigDecimal.valueOf(MemoryAiSupport.confidence(node, DEFAULT_CONFIDENCE));
        return new L2Pipeline.ExtractedScene(sceneKey, title, summary, l1Ids, confidence);
    }

    private static Set<Long> knownIds(List<L1MemoryRecord> newL1, List<L2SceneBlock> existingScenes) {
        var ids = new LinkedHashSet<Long>();
        for (var record : newL1) {
            ids.add(record.id());
        }
        for (var scene : existingScenes) {
            ids.addAll(scene.l1Ids());
        }
        return Set.copyOf(ids);
    }

    private String userPrompt(List<L1MemoryRecord> newL1, List<L2SceneBlock> existingScenes) {
        var l1 = new ArrayList<Map<String, Object>>(newL1.size());
        for (var record : newL1) {
            l1.add(Map.of(
                    "id", record.id(),
                    "content", record.content(),
                    "type", record.type().name(),
                    "priority", record.priority(),
                    "sceneName", record.sceneName()));
        }
        var scenes = new ArrayList<Map<String, Object>>(existingScenes.size());
        for (var scene : existingScenes) {
            scenes.add(Map.of(
                    "sceneKey", scene.sceneKey(),
                    "title", scene.title(),
                    "summary", scene.summary(),
                    "l1Ids", scene.l1Ids()));
        }
        return "新增 L1 记忆与已有场景如下：\n"
                + support.write(Map.of("newL1", l1, "existingScenes", scenes));
    }
}
