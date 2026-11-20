package cn.tolim.pudans.core.ai.harness.compaction;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public final class ContextCompactor {

    private static final String SUMMARY_PROMPT =
            "请把下面的对话压缩成简洁的事实摘要，"
                    + "保留目标、已完成动作、关键结论和待办。";

    private final ChatModel chatModel;

    private final CompactionConfig config;

    public ContextCompactor(ChatModel chatModel, CompactionConfig config) {
        this.chatModel = Objects.requireNonNull(chatModel, "ChatModel 不能为空");
        this.config = Objects.requireNonNull(config, "压缩配置不能为空");
    }

    public Mono<Boolean> compactIfNeeded(AgentState state) {
        return compact(state, false).onErrorResume(error -> {
            log.warn("上下文压缩失败，保留原始消息。", error);
            return Mono.just(false);
        });
    }

    public Mono<Boolean> compactNow(AgentState state) {
        return compact(state, true);
    }

    private Mono<Boolean> compact(AgentState state, boolean force) {
        if (!config.enabled()) {
            return Mono.just(false);
        }
        if (!force && state.messages().size() <= config.triggerMessages()) {
            return Mono.just(false);
        }
        var messages = state.messages();
        var keepFrom = Math.max(0, messages.size() - config.keepMessages());
        var prefix = messages.subList(0, keepFrom);
        if (prefix.isEmpty()) {
            return Mono.just(false);
        }
        var prompt = render(prefix);
        var request = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(SUMMARY_PROMPT),
                        UserMessage.from(prompt)))
                .build();
        return Mono.fromCallable(() -> chatModel.chat(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> response.aiMessage().text())
                .defaultIfEmpty("")
                .map(summary -> {
                    if (!summary.isBlank()) {
                        state.setSummary(merge(state.summary(), summary));
                    }
                    state.replaceMessages(new ArrayList<>(messages.subList(keepFrom, messages.size())));
                    return true;
                });
    }

    private static String merge(String existing, String summary) {
        if (existing == null || existing.isBlank()) {
            return summary.strip();
        }
        return existing.strip() + "\n" + summary.strip();
    }

    private static String render(List<ChatMessage> messages) {
        var text = new StringBuilder();
        for (var message : messages) {
            text.append(messageText(message)).append('\n');
        }
        return text.toString();
    }

    private static String messageText(ChatMessage message) {
        if (message instanceof UserMessage user) {
            var text = new StringBuilder();
            for (var content : user.contents()) {
                if (content instanceof TextContent value) {
                    text.append(value.text());
                }
            }
            return "用户：" + text;
        }
        if (message instanceof AiMessage ai) {
            var text = new StringBuilder("助手：");
            if (ai.text() != null) {
                text.append(ai.text());
            }
            for (var call : ai.toolExecutionRequests()) {
                text.append("\n工具调用：").append(call.name()).append(' ').append(call.arguments());
            }
            return text.toString();
        }
        if (message instanceof ToolExecutionResultMessage tool) {
            return "工具结果：" + tool.text();
        }
        if (message instanceof SystemMessage system) {
            return "系统：" + system.text();
        }
        return message.toString();
    }
}
