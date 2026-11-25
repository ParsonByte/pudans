package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.infra.utils.Digests;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ReMeMemory {

    private final ReMeMemoryProperties properties;

    private final AiProviderHttp http;

    private final ReMeMemoryClient suppliedClient;

    private volatile ReMeMemoryClient ownedClient;

    @Autowired
    public ReMeMemory(ReMeMemoryProperties properties, AiProviderHttp http) {
        this(properties, http, null);
    }

    ReMeMemory(ReMeMemoryProperties properties, AiProviderHttp http, ReMeMemoryClient suppliedClient) {
        this.properties = properties;
        this.http = http;
        this.suppliedClient = suppliedClient;
    }

    public Mono<String> recall(String ownerId, String workspaceId, String query) {
        if (!properties.configured() || !StringUtils.hasText(query)) {
            return Mono.just("");
        }
        return Mono.defer(() -> client()
                        .search(workspace(ownerId, workspaceId), query, properties.normalizedTopK())
                        .map(this::recallText)
                        .defaultIfEmpty(""))
                .onErrorResume(error -> {
                    log.warn("ReMe 长期记忆检索失败：{}", error.getMessage());
                    return Mono.just("");
                });
    }

    public Mono<Void> record(
            String ownerId, String workspaceId, String userText, String assistantText) {
        if (!properties.configured()) {
            return Mono.empty();
        }
        var messages = new ArrayList<ReMeMemoryClient.Message>();
        if (StringUtils.hasText(userText)) {
            messages.add(new ReMeMemoryClient.Message("user", userText.trim()));
        }
        if (StringUtils.hasText(assistantText)) {
            messages.add(new ReMeMemoryClient.Message("assistant", assistantText.trim()));
        }
        if (messages.isEmpty()) {
            return Mono.empty();
        }
        return Mono.defer(() -> client().add(workspace(ownerId, workspaceId), List.copyOf(messages)))
                .then()
                .onErrorResume(error -> {
                    log.warn("ReMe 长期记忆写入失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }

    private ReMeMemoryClient client() {
        if (suppliedClient != null) {
            return suppliedClient;
        }
        if (ownedClient == null) {
            synchronized (this) {
                if (ownedClient == null) {
                    ownedClient = new ReMeMemoryClient(
                            http, properties.getApiBaseUrl().trim(), properties.normalizedTimeout());
                }
            }
        }
        return ownedClient;
    }

    private String recallText(ReMeMemoryClient.Answer response) {
        if (response == null || !response.success()) {
            return "";
        }
        var answer = response.answer();
        var text = StringUtils.hasText(answer)
                ? answer.trim()
                : response.memories().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("");
        var limit = properties.normalizedMaxContextChars();
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private String workspace(String ownerId, String workspaceId) {
        var digest = Digests.sha256Hex(ownerId + "\0" + workspaceId);
        return properties.getWorkspacePrefix().trim() + ":" + digest;
    }
}