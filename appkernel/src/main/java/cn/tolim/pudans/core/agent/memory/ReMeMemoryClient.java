package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class ReMeMemoryClient {

    private static final String SUMMARY_ENDPOINT = "/summary_personal_memory";

    private static final String RETRIEVE_ENDPOINT = "/retrieve_personal_memory";

    private final AiProviderHttp http;

    private final String apiBaseUrl;

    private final Duration timeout;

    public ReMeMemoryClient(AiProviderHttp http, String apiBaseUrl, Duration timeout) {
        this.http = http;
        this.apiBaseUrl = normalized(apiBaseUrl);
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    // 将真实对话轨迹写入 ReMe 个人记忆服务。
    public Mono<Answer> add(String workspaceId, List<Message> messages) {
        var trajectory = new LinkedHashMap<String, Object>();
        trajectory.put("messages", messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());
        var body = new LinkedHashMap<String, Object>();
        body.put("workspace_id", workspaceId);
        body.put("trajectories", List.of(trajectory));
        return post(SUMMARY_ENDPOINT, Map.copyOf(body));
    }

    // 从 ReMe 个人记忆服务检索与查询相关的记忆。
    public Mono<Answer> search(String workspaceId, String query, int topK) {
        var body = new LinkedHashMap<String, Object>();
        body.put("workspace_id", workspaceId);
        body.put("query", query);
        body.put("top_k", topK);
        return post(RETRIEVE_ENDPOINT, Map.copyOf(body));
    }

    private Mono<Answer> post(String path, Map<String, Object> body) {
        return http.postJson(
                        URI.create(apiBaseUrl + path),
                        Map.of("content-type", "application/json"),
                        body,
                        timeout)
                .map(ReMeMemoryClient::decode);
    }

    private static Answer decode(String raw) {
        try {
            var root = JsonUtils.mapper().readTree(raw);
            var success = root.path("success").isBoolean()
                    ? root.path("success").booleanValue()
                    : true;
            var memories = new ArrayList<String>();
            for (var item : root.path("metadata").path("memory_list")) {
                var content = item.path("content").asText("");
                if (!content.isBlank()) {
                    memories.add(content);
                }
            }
            return new Answer(root.path("answer").asText(""), success, List.copyOf(memories));
        } catch (Exception error) {
            throw new IllegalStateException("无法解析 ReMe 记忆服务响应。", error);
        }
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public record Message(String role, String content) {}

    public record Answer(String answer, boolean success, List<String> memories) {

        public Answer {
            memories = memories == null ? List.of() : List.copyOf(memories);
        }
    }
}