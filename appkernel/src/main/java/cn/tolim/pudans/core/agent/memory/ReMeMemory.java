package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.infra.utils.Digests;
import io.agentscope.core.memory.reme.ReMeAddRequest;
import io.agentscope.core.memory.reme.ReMeClient;
import io.agentscope.core.memory.reme.ReMeMessage;
import io.agentscope.core.memory.reme.ReMeSearchRequest;
import io.agentscope.core.memory.reme.ReMeSearchResponse;
import io.agentscope.core.memory.reme.ReMeTrajectory;
import jakarta.annotation.PreDestroy;
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

    private final ReMeClient suppliedClient;

    private volatile ReMeClient ownedClient;

    @Autowired
    public ReMeMemory(ReMeMemoryProperties properties) {
        this(properties, null);
    }

    ReMeMemory(ReMeMemoryProperties properties, ReMeClient suppliedClient) {
        this.properties = properties;
        this.suppliedClient = suppliedClient;
    }

    public Mono<String> recall(String ownerId, String workspaceId, String query) {
        if (!properties.configured() || !StringUtils.hasText(query)) {
            return Mono.just("");
        }
        return Mono.defer(() -> client()
                        .search(ReMeSearchRequest.builder()
                                .workspaceId(workspace(ownerId, workspaceId))
                                .query(query)
                                .topK(properties.normalizedTopK())
                                .build())
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
        var messages = new ArrayList<ReMeMessage>();
        if (StringUtils.hasText(userText)) {
            messages.add(ReMeMessage.builder()
                    .role("user")
                    .content(userText.trim())
                    .build());
        }
        if (StringUtils.hasText(assistantText)) {
            messages.add(ReMeMessage.builder()
                    .role("assistant")
                    .content(assistantText.trim())
                    .build());
        }
        if (messages.isEmpty()) {
            return Mono.empty();
        }
        var request = ReMeAddRequest.builder()
                .workspaceId(workspace(ownerId, workspaceId))
                .trajectories(List.of(
                        ReMeTrajectory.builder().messages(List.copyOf(messages)).build()))
                .build();
        return Mono.defer(() -> client().add(request))
                .then()
                .onErrorResume(error -> {
                    log.warn("ReMe 长期记忆写入失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }

    private ReMeClient client() {
        if (suppliedClient != null) {
            return suppliedClient;
        }
        if (ownedClient == null) {
            synchronized (this) {
                if (ownedClient == null) {
                    ownedClient = new ReMeClient(
                            properties.getApiBaseUrl().trim(), properties.normalizedTimeout());
                }
            }
        }
        return ownedClient;
    }

    private String recallText(ReMeSearchResponse response) {
        if (response == null || Boolean.FALSE.equals(response.getSuccess())) {
            return "";
        }
        var answer = response.getAnswer();
        var text = StringUtils.hasText(answer)
                ? answer.trim()
                : response.getMemories() == null
                        ? ""
                        : response.getMemories().stream()
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

    @PreDestroy
    void shutdown() {
        var client = ownedClient;
        if (client != null) {
            client.shutdown();
        }
    }
}
