package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2PipelineService;
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
public class MemorySceneAiService {

    private static final int MAX_OUTPUT_TOKENS = 2048;

    private static final double DEFAULT_CONFIDENCE = 0.7;

    private static final String SYSTEM_PROMPT = """
            你是记忆场景聚合器，负责把新增 L1 记忆归并到场景块中。
            规则：
            1. l1Ids 只能引用输入 newL1 中的 id 或 existingScenes 中的 l1Ids。
            2. 每个场景必须包含至少一个有效 l1Id；同一个 l1Id 不得出现在多个场景。
            3. sceneKey 使用稳定的英文小写短横线标识；title 和 summary 使用中文简要概括。
            4. confidence 为 0 到 1 之间的小数；没有合适场景时输出 {"scenes":[]}。
            只输出一个 JSON 对象，不要输出解释、Markdown 代码块或任何多余文本。
            JSON 结构：
            {"scenes":[{"sceneKey":"release-process","title":"发布流程",
              "summary":"关于发布节奏与注意事项的汇总","l1Ids":[1,2],"confidence":0.8}]}
            """;

    private final MemoryAiSupport support;

    public Mono<List<L2PipelineService.ExtractedScene>> extract(
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
                        MemoryAiSupport.combine(systemPrompt, SYSTEM_PROMPT),
                        userPrompt(pending, scenes),
                        MAX_OUTPUT_TOKENS)
                .map(result -> extracted(result.content(), pending, scenes));
    }

    private List<L2PipelineService.ExtractedScene> extracted(
            String content, List<L1MemoryRecord> newL1, List<L2SceneBlock> existingScenes) {
        var knownIds = knownIds(newL1, existingScenes);
        var root = support.parseObject(content);
        var scenesNode = root.get("scenes");
        if (scenesNode == null || !scenesNode.isArray()) {
            throw MemoryAiSupport.invalid("场景抽取结果缺少 scenes 数组。", null);
        }
        var scenes = new ArrayList<L2PipelineService.ExtractedScene>();
        for (var node : scenesNode) {
            var scene = scene(node, knownIds);
            if (scene != null) {
                scenes.add(scene);
            }
        }
        return List.copyOf(scenes);
    }

    private static L2PipelineService.ExtractedScene scene(JsonNode node, Set<Long> knownIds) {
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
        return new L2PipelineService.ExtractedScene(sceneKey, title, summary, l1Ids, confidence);
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
