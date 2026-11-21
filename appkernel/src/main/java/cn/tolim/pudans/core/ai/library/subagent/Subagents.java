package cn.tolim.pudans.core.ai.library.subagent;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.index.query.Queries.equal;

import cn.tolim.pudans.contract.ai.AiSubagent;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class Subagents {

    public static final String CODE_SUBAGENT_NOT_FOUND = "ai-subagent-not-found";

    private final ReactiveExtensionClient client;

    public Flux<AiSubagent> byAgent(String agentName) {
        requireAgentName(agentName);
        var options = ListOptions.builder()
                .andQuery(equal("spec.enabled", true))
                .andQuery(equal("spec.agents", agentName))
                .build();
        return client.listAll(AiSubagent.class, options, defaultSort());
    }

    public Mono<AiSubagent> resolve(String agentName, String name) {
        if (!StringUtils.hasText(name)) {
            return Mono.error(new ServerWebInputException("name 不能为空。"));
        }
        return byAgent(agentName)
                .filter(subagent -> name.equals(subagent.getMetadata().getName()))
                .next()
                .switchIfEmpty(Mono.error(
                        new ApiException(HttpStatus.NOT_FOUND, CODE_SUBAGENT_NOT_FOUND, "子代理不存在：" + name)));
    }

    public Mono<AiSubagent> create(AiSubagent subagent) {
        validate(subagent);
        return client.create(subagent);
    }

    public Mono<AiSubagent> update(AiSubagent subagent) {
        validate(subagent);
        return client.update(subagent);
    }

    public Mono<AiSubagent> delete(String name) {
        return client.get(AiSubagent.class, name).flatMap(client::delete);
    }

    public void validate(AiSubagent subagent) {
        var spec = subagent == null ? null : subagent.getSpec();
        if (spec == null) {
            throw new ServerWebInputException("子代理配置不能为空。");
        }
        if (!StringUtils.hasText(spec.getDisplayName())) {
            throw new ServerWebInputException("子代理展示名称不能为空。");
        }
        if (!StringUtils.hasText(spec.getPrompt())) {
            throw new ServerWebInputException("子代理提示词不能为空。");
        }
        requireTextEntries(spec.getSkills(), "技能");
        requireTextEntries(spec.getMcpServers(), "MCP 服务器");
    }

    private static void requireTextEntries(List<String> values, String label) {
        if (values == null) {
            return;
        }
        if (values.stream().anyMatch(value -> !StringUtils.hasText(value))) {
            throw new ServerWebInputException("子代理关联的" + label + "名称不能为空。");
        }
    }

    private static void requireAgentName(String agentName) {
        if (!StringUtils.hasText(agentName)) {
            throw new ServerWebInputException("agent 不能为空。");
        }
    }
}