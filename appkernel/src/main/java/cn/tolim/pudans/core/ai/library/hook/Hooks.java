package cn.tolim.pudans.core.ai.library.hook;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.contract.ai.resource.AiHook;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimes;
import cn.tolim.pudans.core.webhook.WebhookEventPublisher;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class Hooks {

    public static final String EVENT_TYPE_PREFIX = "ai.hook.";

    private static final String TOOL_KEY = "tool";

    private static final String TOOL_NAME_KEY = "toolName";

    private static final String TRACE_FLOW_ID_KEY = "traceFlowId";

    private final ReactiveExtensionClient client;

    private final WebhookEventPublisher eventPublisher;

    private final @Nullable AgentRuntimes agentRuntimes;

    Hooks(ReactiveExtensionClient client, WebhookEventPublisher eventPublisher) {
        this(client, eventPublisher, null);
    }

    @Autowired
    public Hooks(
            ReactiveExtensionClient client,
            WebhookEventPublisher eventPublisher,
            @Nullable AgentRuntimes agentRuntimes) {
        this.client = client;
        this.eventPublisher = eventPublisher;
        this.agentRuntimes = agentRuntimes;
    }

    public Flux<AiHook> byAgent(String agentName, AiHook.@Nullable Event event) {
        requireAgentName(agentName);
        var options = ListOptions.builder()
                .andQuery(equal("spec.enabled", true))
                .andQuery(equal("spec.agents", agentName));
        if (event != null) {
            options.andQuery(equal("spec.event", event));
        }
        return client.listAll(AiHook.class, options.build(), defaultSort());
    }

    public Flux<AiHook> matching(String agentName, AiHook.Event event, @Nullable String toolName) {
        return byAgent(agentName, event).filter(hook -> matches(hook.getSpec(), event, toolName));
    }

    public Mono<Void> dispatch(String agentName, AiHook.Event event, @Nullable Map<String, Object> payload) {
        var safePayload = payload == null ? Map.<String, Object>of() : payload;
        return matching(agentName, event, toolName(safePayload))
                .concatMap(hook -> Mono.fromRunnable(() -> publish(agentName, event, hook, safePayload))
                        .then(execute(agentName, hook)))
                .then()
                .onErrorResume(error -> {
                    log.warn("无法分发 Hook 事件 [{}] [{}]", agentName, event, error);
                    return Mono.empty();
                });
    }

    private Mono<Void> execute(String agentName, AiHook hook) {
        var spec = hook.getSpec();
        if (agentRuntimes == null
                || spec == null
                || !StringUtils.hasText(spec.getCommand())) {
            return Mono.empty();
        }
        return client.get(AiAgent.class, agentName)
                .filter(agent -> agent.getSpec() != null
                        && agent.getSpec().getRuntime() != null
                        && agent.getSpec().getRuntime().getProvider() != AiAgent.RuntimeProvider.NONE)
                .flatMap(agent -> agentRuntimes
                        .execInternal(agentName, spec.getCommand(), null, spec.getTimeoutSeconds())
                        .then())
                .onErrorResume(error -> {
                    log.warn("Hook 命令执行失败 [{}] [{}]", agentName, hook.getMetadata().getName(), error);
                    return Mono.empty();
                });
    }

    public void validate(AiHook hook) {
        var spec = hook == null ? null : hook.getSpec();
        if (spec == null) {
            throw new ServerWebInputException("Hook 配置不能为空。");
        }
        if (!StringUtils.hasText(spec.getDisplayName())) {
            throw new ServerWebInputException("Hook 展示名称不能为空。");
        }
        if (spec.getEvent() == null) {
            throw new ServerWebInputException("Hook 事件不能为空。");
        }
        if (!StringUtils.hasText(spec.getCommand())) {
            throw new ServerWebInputException("Hook 命令不能为空。");
        }
        if (spec.getTimeoutSeconds() < AiHook.MIN_TIMEOUT_SECONDS
                || spec.getTimeoutSeconds() > AiHook.MAX_TIMEOUT_SECONDS) {
            throw new ServerWebInputException("Hook 超时时间必须在 1 到 600 秒之间。");
        }
    }

    static boolean matches(AiHook.@Nullable Spec spec, AiHook.Event event, @Nullable String toolName) {
        if (spec == null) {
            return false;
        }
        if (event != AiHook.Event.PRE_TOOL_USE && event != AiHook.Event.POST_TOOL_USE) {
            return true;
        }
        var matcher = spec.getMatcher();
        if (!StringUtils.hasText(matcher)) {
            return true;
        }
        var pattern = matcher.trim();
        if ("*".equals(pattern)) {
            return true;
        }
        if (!StringUtils.hasText(toolName)) {
            return false;
        }
        if (pattern.endsWith("*")) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }
        return toolName.startsWith(pattern);
    }

    private void publish(String agentName, AiHook.Event event, AiHook hook, Map<String, Object> payload) {
        var spec = hook.getSpec();
        var data = new LinkedHashMap<String, Object>();
        data.put("agent", agentName);
        data.put("event", event.name());
        data.put("hook", hook.getMetadata().getName());
        data.put("command", spec.getCommand());
        data.put("timeoutSeconds", spec.getTimeoutSeconds());
        data.put("payload", payload);
        eventPublisher.publish(EVENT_TYPE_PREFIX + event.name(), data, traceFlowId(payload));
    }

    private static void requireAgentName(String agentName) {
        if (!StringUtils.hasText(agentName)) {
            throw new ServerWebInputException("agent 不能为空。");
        }
    }

    private static @Nullable String toolName(Map<String, Object> payload) {
        var tool = payload.get(TOOL_KEY);
        if (tool instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        var toolName = payload.get(TOOL_NAME_KEY);
        return toolName instanceof String value && StringUtils.hasText(value) ? value : null;
    }

    private static @Nullable String traceFlowId(Map<String, Object> payload) {
        var value = payload.get(TRACE_FLOW_ID_KEY);
        return value instanceof String text && StringUtils.hasText(text) ? text : null;
    }
}