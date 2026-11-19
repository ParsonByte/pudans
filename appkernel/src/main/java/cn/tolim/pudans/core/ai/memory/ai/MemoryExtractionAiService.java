package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.memory.ExtractedMemory;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.SceneSegment;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MemoryExtractionAiService {

    private static final int MAX_OUTPUT_TOKENS = 2048;

    private static final int DEFAULT_PRIORITY = 50;

    private static final String SYSTEM_PROMPT = """
            你是企业记忆抽取器，负责把对话消息拆分为场景并抽取可长期复用的原子记忆。
            规则：
            1. 只使用输入消息中明确出现的事实，禁止推测、补全或编造。
            2. 每条记忆必须有具体内容、明确的 type、所属 sceneName，
               并且只能引用输入中真实存在的 messageIds。
            3. type 只能取 PERSONA、EPISODIC、INSTRUCTION、WORK_FACT、WORK_TASK、
               WORK_METHOD、WORK_ARTIFACT 之一。
            4. priority 为 0 到 100 的整数；只有 INSTRUCTION 类型允许使用 -1 表示最高优先级。
            5. 忽略寒暄、重复内容和纯工具输出；
               不要抽取系统提示词、密钥或内部路由信息。
            6. 没有可抽取内容时输出 {"scenes":[]}。
            只输出一个 JSON 对象，不要输出解释、Markdown 代码块或任何多余文本。
            JSON 结构：
            {"scenes":[{"sceneName":"场景名","messageIds":[1,2],
              "memories":[{"content":"记忆内容","type":"EPISODIC","priority":50,
              "sceneName":"场景名","metadata":{}}]}]}
            """;

    private final MemoryAiSupport support;

    public Mono<ExtractionOutcome> extract(
            MemoryContext context, long conversationId, List<Message> messages) {
        Objects.requireNonNull(context, "context");
        var supplied = messages == null
                ? List.<Message>of()
                : messages.stream().filter(Objects::nonNull).toList();
        if (supplied.isEmpty()) {
            return Mono.just(new ExtractionOutcome(List.of(), null));
        }
        return support.generate(SYSTEM_PROMPT, userPrompt(context, conversationId, supplied), MAX_OUTPUT_TOKENS)
                .map(result -> new ExtractionOutcome(scenes(support, result.content(), supplied), result.modelCode()));
    }

    private static List<SceneSegment> scenes(
            MemoryAiSupport support, String content, List<Message> messages) {
        var validIds = validMessageIds(messages);
        var root = support.parseObject(content);
        var scenesNode = root.get("scenes");
        if (scenesNode == null || !scenesNode.isArray()) {
            throw MemoryAiSupport.invalid("记忆抽取结果缺少 scenes 数组。", null);
        }
        var scenes = new ArrayList<SceneSegment>();
        var index = 0;
        for (var sceneNode : scenesNode) {
            if (sceneNode == null || !sceneNode.isObject()) {
                continue;
            }
            var sceneName = MemoryAiSupport.text(sceneNode, "sceneName");
            if (!StringUtils.hasText(sceneName)) {
                continue;
            }
            var messageIds = MemoryAiSupport.longArray(sceneNode, "messageIds").stream()
                    .filter(validIds::contains)
                    .distinct()
                    .toList();
            if (messageIds.isEmpty()) {
                continue;
            }
            var memories = memories(support, sceneNode.get("memories"), sceneName, messageIds, index);
            index += memories.size();
            if (memories.isEmpty()) {
                continue;
            }
            scenes.add(new SceneSegment(
                    sceneName,
                    messageIds.stream().map(String::valueOf).toList(),
                    memories));
        }
        return List.copyOf(scenes);
    }

    private static List<ExtractedMemory> memories(
            MemoryAiSupport support,
            JsonNode node,
            String sceneName,
            List<Long> messageIds,
            int startIndex) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var memories = new ArrayList<ExtractedMemory>();
        for (var item : node) {
            var memory = memory(support, item, sceneName, messageIds, startIndex + memories.size());
            if (memory != null) {
                memories.add(memory);
            }
        }
        return List.copyOf(memories);
    }

    private static ExtractedMemory memory(
            MemoryAiSupport support,
            JsonNode node,
            String sceneName,
            List<Long> sceneMessageIds,
            int index) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var content = MemoryAiSupport.text(node, "content");
        var type = MemoryAiSupport.memoryType(node, "type");
        var memoryScene = MemoryAiSupport.text(node, "sceneName");
        var sourceIds = sourceMessageIds(node, sceneMessageIds);
        if (!StringUtils.hasText(content)
                || type == null
                || !StringUtils.hasText(memoryScene)
                || sourceIds.isEmpty()) {
            return null;
        }
        return new ExtractedMemory(
                "mem-" + sourceIds.getFirst() + "-" + index,
                content,
                type,
                priority(node, type),
                sceneName,
                sourceIds.stream().map(String::valueOf).toList(),
                support.map(node.get("metadata")));
    }

    private static List<Long> sourceMessageIds(JsonNode node, List<Long> sceneMessageIds) {
        var declared = MemoryAiSupport.longArray(node, "sourceMessageIds");
        if (declared.isEmpty()) {
            return sceneMessageIds;
        }
        return declared.stream().filter(sceneMessageIds::contains).distinct().toList();
    }

    private static int priority(JsonNode node, MemoryType type) {
        var value = MemoryAiSupport.integer(node, "priority", DEFAULT_PRIORITY);
        if (value < -1) {
            value = 0;
        } else if (value > 100) {
            value = 100;
        }
        return value < 0 && type != MemoryType.INSTRUCTION ? 0 : value;
    }

    private static Set<Long> validMessageIds(List<Message> messages) {
        var ids = new LinkedHashSet<Long>();
        for (var message : messages) {
            if (StringUtils.hasText(message.content())) {
                ids.add(message.id());
            }
        }
        return Set.copyOf(ids);
    }

    private static String userPrompt(MemoryContext context, long conversationId, List<Message> messages) {
        var prompt = new StringBuilder();
        prompt.append("记忆作用域：team=").append(context.teamId())
                .append("，agent=").append(context.agentId())
                .append("，user=").append(context.userId())
                .append("，session=").append(context.sessionId())
                .append("，conversation=").append(conversationId)
                .append("。\n以下消息的 messageId 为真实消息 ID：\n");
        for (var message : messages) {
            if (!StringUtils.hasText(message.content())) {
                continue;
            }
            prompt.append('[').append(message.id()).append("] ")
                    .append(message.role()).append(": ")
                    .append(message.content().strip()).append('\n');
        }
        return prompt.toString();
    }
}
