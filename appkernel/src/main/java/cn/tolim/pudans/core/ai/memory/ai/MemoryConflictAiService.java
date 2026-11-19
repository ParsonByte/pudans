package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.memory.DedupAction;
import cn.tolim.pudans.core.ai.memory.DedupDecision;
import cn.tolim.pudans.core.ai.memory.L1DedupService.DedupCandidate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MemoryConflictAiService {

    private static final int MAX_OUTPUT_TOKENS = 2048;

    private static final String SYSTEM_PROMPT = """
            你是记忆冲突判定器，负责对每条新记忆给出 store、update、merge 或 skip 决策。
            规则：
            1. 只能使用输入中提供的 recordId 和 targetIds；targetIds 必须来自 existing 列表。
            2. UPDATE 和 MERGE 必须提供非空 targetIds；STORE 和 SKIP 不得提供 targetIds。
            3. mergedType 只能取 MemoryType 枚举值；mergedPriority 为 -1 到 100 的整数。
            4. 每条新记忆必须恰好输出一个决策，不要遗漏任何 recordId。
            5. 没有冲突时使用 STORE；完全重复且无新增信息时使用 SKIP。
            只输出一个 JSON 对象，不要输出解释、Markdown 代码块或任何多余文本。
            JSON 结构：
            {"decisions":[{"recordId":"mem-1","action":"STORE|UPDATE|MERGE|SKIP",
              "targetIds":["old-1"],"mergedContent":"合并后的内容","mergedType":"EPISODIC",
              "mergedPriority":50,"mergedTimestamps":["2026-01-01T00:00:00Z"]}]}
            """;

    private final MemoryAiSupport support;

    public Mono<List<DedupDecision>> judge(
            String systemPrompt, List<DedupCandidate> candidates) {
        var batch = candidates == null ? List.<DedupCandidate>of() : List.copyOf(candidates);
        if (batch.isEmpty()) {
            return Mono.just(List.of());
        }
        return Mono.defer(() -> support.generate(
                        MemoryAiSupport.combine(systemPrompt, SYSTEM_PROMPT),
                        userPrompt(batch),
                        MAX_OUTPUT_TOKENS)
                .map(result -> decisions(result.content(), batch))
                .onErrorResume(error -> Mono.just(storeAll(batch))));
    }

    private List<DedupDecision> decisions(String content, List<DedupCandidate> candidates) {
        var root = support.parseObject(content);
        var decisionsNode = root.get("decisions");
        if (decisionsNode == null || !decisionsNode.isArray()) {
            throw MemoryAiSupport.invalid("记忆冲突判定结果缺少 decisions 数组。", null);
        }
        var parsed = new LinkedHashMap<String, DedupDecision>();
        for (var node : decisionsNode) {
            var decision = decision(node);
            if (decision != null) {
                parsed.putIfAbsent(decision.recordId(), decision);
            }
        }
        var decisions = new ArrayList<DedupDecision>(candidates.size());
        for (var candidate : candidates) {
            var recordId = candidate.memory().recordId();
            decisions.add(parsed.getOrDefault(recordId, DedupDecision.store(recordId)));
        }
        return List.copyOf(decisions);
    }

    private static DedupDecision decision(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var recordId = MemoryAiSupport.text(node, "recordId");
        if (!StringUtils.hasText(recordId)) {
            return null;
        }
        var action = MemoryAiSupport.dedupAction(node);
        if (action == null) {
            action = DedupAction.STORE;
        }
        var targets = action.replacesExisting()
                ? MemoryAiSupport.stringArray(node, "targetIds")
                : List.<String>of();
        if (action.replacesExisting() && targets.isEmpty()) {
            return DedupDecision.store(recordId);
        }
        var mergedPriority = MemoryAiSupport.optionalInteger(node, "mergedPriority");
        if (mergedPriority != null && (mergedPriority < -1 || mergedPriority > 100)) {
            mergedPriority = null;
        }
        return new DedupDecision(
                recordId,
                action,
                targets,
                MemoryAiSupport.text(node, "mergedContent"),
                MemoryAiSupport.memoryType(node, "mergedType"),
                mergedPriority,
                MemoryAiSupport.stringArray(node, "mergedTimestamps"));
    }

    private String userPrompt(List<DedupCandidate> candidates) {
        var payload = new ArrayList<Map<String, Object>>(candidates.size());
        for (var candidate : candidates) {
            var memory = candidate.memory();
            payload.add(Map.of(
                    "recordId", memory.recordId(),
                    "content", memory.content(),
                    "type", memory.type().name(),
                    "priority", memory.priority(),
                    "sceneName", memory.sceneName(),
                    "existing", existing(candidate)));
        }
        return "待判定的新记忆及候选旧记忆如下：\n" + support.write(Map.of("candidates", payload));
    }

    private static List<Map<String, Object>> existing(DedupCandidate candidate) {
        var existing = new ArrayList<Map<String, Object>>(candidate.candidates().size());
        for (var memory : candidate.candidates()) {
            existing.add(Map.of(
                    "recordId", memory.recordId(),
                    "content", memory.content(),
                    "status", memory.status().name()));
        }
        return existing;
    }

    private static List<DedupDecision> storeAll(List<DedupCandidate> candidates) {
        return candidates.stream()
                .map(candidate -> DedupDecision.store(candidate.memory().recordId()))
                .toList();
    }
}
