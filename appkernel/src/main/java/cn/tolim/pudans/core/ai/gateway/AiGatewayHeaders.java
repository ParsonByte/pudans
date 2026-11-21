package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

public final class AiGatewayHeaders {

    public static final String CONVERSATION_ID = "x-conversation-id";

    public static final String SESSION_ID = "x-session-id";

    public static final String CHAT_ID = "x-chat-id";

    public static final String THREAD_ID = "x-thread-id";

    public static final String CLAUDE_SESSION = "x-claude-code-session-id";

    public static final String AGENT_SOURCE = "x-agent-source";

    public static final String AGENT_NAME = "x-agent-name";

    public static final String CHAT_MODE = "x-chat-mode";

    public static final String ROUTE_GROUP = "x-route-group";

    public static final String IDEMPOTENCY = "Idempotency-Key";

    private AiGatewayHeaders() {}

    public static String sessionKey(ServerRequest request) {
        var value = providedSessionKey(request);
        return StringUtils.hasText(value) ? value : UUID.randomUUID().toString();
    }

    public static @Nullable String providedSessionKey(ServerRequest request) {
        return firstText(
                header(request, CLAUDE_SESSION),
                header(request, SESSION_ID),
                header(request, CONVERSATION_ID),
                header(request, CHAT_ID),
                header(request, THREAD_ID));
    }

    public static @Nullable String sessionId(ServerRequest request) {
        return firstText(header(request, SESSION_ID), header(request, CLAUDE_SESSION));
    }

    public static String agentSource(ServerRequest request, AiProvider.Protocol protocol) {
        var explicit = header(request, AGENT_SOURCE);
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        if (StringUtils.hasText(header(request, CLAUDE_SESSION))) {
            return "claude-code";
        }
        return switch (protocol) {
            case ANTHROPIC -> "anthropic";
            case GEMINI -> "gemini";
            default -> "openai";
        };
    }

    public static String idempotencyKey(ServerRequest request, String requestId) {
        var value = header(request, IDEMPOTENCY);
        return StringUtils.hasText(value) ? value : requestId;
    }

    public static @Nullable String agentName(ServerRequest request) {
        return header(request, AGENT_NAME);
    }

    public static @Nullable String mode(ServerRequest request) {
        return header(request, CHAT_MODE);
    }

    public static @Nullable String routeGroup(ServerRequest request) {
        return header(request, ROUTE_GROUP);
    }

    public static @Nullable Long conversationId(ServerRequest request) {
        var value = header(request, CONVERSATION_ID);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            var parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static @Nullable String header(ServerRequest request, String name) {
        var value = request.headers().firstHeader(name);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static @Nullable String firstText(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
