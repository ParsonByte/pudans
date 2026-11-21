package cn.tolim.pudans.core.ai.library.environment;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.index.query.Queries.equal;

import cn.tolim.pudans.contract.ai.AiEnvironmentVariable;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class EnvironmentVariables {

    private static final Pattern NAME_PATTERN = Pattern.compile(AiEnvironmentVariable.NAME_PATTERN);

    private final ReactiveExtensionClient client;

    public Mono<AiEnvironmentVariable> create(AiEnvironmentVariable variable) {
        validate(variable);
        normalizeOnCreate(variable);
        return client.create(variable);
    }

    public Mono<AiEnvironmentVariable> update(AiEnvironmentVariable variable) {
        validate(variable);
        var name = nameOf(variable);
        return client.get(AiEnvironmentVariable.class, name).flatMap(existing -> {
            mergeValue(existing, variable);
            return client.update(variable);
        });
    }

    public Mono<AiEnvironmentVariable> delete(String name) {
        return client.get(AiEnvironmentVariable.class, name).flatMap(client::delete);
    }

    public Flux<AiEnvironmentVariable> byAgent(String agentName) {
        requireAgentName(agentName);
        var options = ListOptions.builder()
                .andQuery(equal("spec.enabled", true))
                .andQuery(equal("spec.agents", agentName))
                .build();
        return client.listAll(AiEnvironmentVariable.class, options, defaultSort());
    }

    public Flux<MaskedEnvironmentVariable> maskedByAgent(String agentName) {
        return byAgent(agentName).map(EnvironmentVariables::mask);
    }

    public Mono<Map<String, String>> resolveValues(String agentName) {
        return byAgent(agentName)
                .filter(variable -> StringUtils.hasText(variable.getSpec().getValue()))
                .collectMap(
                        variable -> variable.getSpec().getName(),
                        variable -> variable.getSpec().getValue());
    }

    public Mono<AiEnvironmentVariable> updateValue(String name, @Nullable String value, boolean clear) {
        if (!StringUtils.hasText(name)) {
            return Mono.error(new ServerWebInputException("name 不能为空。"));
        }
        return client.get(AiEnvironmentVariable.class, name).flatMap(existing -> {
            var spec = existing.getSpec();
            if (clear) {
                spec.setValue(null);
                spec.setConfigured(false);
            } else if (StringUtils.hasText(value)) {
                spec.setValue(value);
                spec.setConfigured(true);
            } else {
                spec.setConfigured(StringUtils.hasText(spec.getValue()));
            }
            return client.update(existing);
        });
    }

    public void validate(AiEnvironmentVariable variable) {
        var spec = variable == null ? null : variable.getSpec();
        if (spec == null) {
            throw new ServerWebInputException("环境变量配置不能为空。");
        }
        if (!StringUtils.hasText(spec.getName()) || !NAME_PATTERN.matcher(spec.getName()).matches()) {
            throw new ServerWebInputException(
                    "环境变量名称必须匹配 " + AiEnvironmentVariable.NAME_PATTERN + "。");
        }
    }

    public static MaskedEnvironmentVariable mask(AiEnvironmentVariable variable) {
        var spec = variable.getSpec();
        var metadata = variable.getMetadata();
        return new MaskedEnvironmentVariable(
                metadata == null ? null : metadata.getName(),
                spec == null ? null : spec.getName(),
                spec != null && StringUtils.hasText(spec.getValue()),
                spec == null ? null : spec.getDescription(),
                spec != null && spec.isEnabled(),
                spec == null || spec.getAgents() == null ? List.of() : List.copyOf(spec.getAgents()));
    }

    private static void normalizeOnCreate(AiEnvironmentVariable variable) {
        var spec = variable.getSpec();
        if (!spec.isConfigured()) {
            spec.setValue(null);
        }
        spec.setConfigured(StringUtils.hasText(spec.getValue()));
    }

    private static void mergeValue(AiEnvironmentVariable existing, AiEnvironmentVariable requested) {
        var spec = requested.getSpec();
        var current = existing.getSpec();
        if (!spec.isConfigured()) {
            spec.setValue(null);
        } else if (!StringUtils.hasText(spec.getValue())) {
            spec.setValue(current == null ? null : current.getValue());
        }
        spec.setConfigured(StringUtils.hasText(spec.getValue()));
    }

    private static String nameOf(AiEnvironmentVariable variable) {
        var metadata = variable.getMetadata();
        if (metadata == null || !StringUtils.hasText(metadata.getName())) {
            throw new ServerWebInputException("metadata.name 不能为空。");
        }
        return metadata.getName();
    }

    private static void requireAgentName(String agentName) {
        if (!StringUtils.hasText(agentName)) {
            throw new ServerWebInputException("agent 不能为空。");
        }
    }

    public record MaskedEnvironmentVariable(
            @Nullable String resourceName,
            @Nullable String name,
            boolean configured,
            @Nullable String description,
            boolean enabled,
            List<String> agents) {}
}