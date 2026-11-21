package cn.tolim.pudans.core.ai.library.instruction;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.ai.AiInstruction;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class Instructions {

    static final String CODE_INVALID = "ai-instruction-invalid";

    private final ReactiveExtensionClient client;

    public static Mono<Void> validate(AiInstruction instruction) {
        return Mono.fromRunnable(() -> {
            var spec = instruction == null ? null : instruction.getSpec();
            if (spec == null) {
                throw invalid("指令配置不能为空。");
            }
            if (!StringUtils.hasText(spec.getDisplayName())) {
                throw invalid("指令 displayName 不能为空。");
            }
            if (!StringUtils.hasText(spec.getContent())) {
                throw invalid("指令 content 不能为空。");
            }
            if (spec.getScope() == null) {
                throw invalid("指令 scope 不能为空。");
            }
            requireNoBlankEntries(spec.getAgents());
        });
    }

    public Flux<AiInstruction> byAgent(String agentName) {
        return client.listAll(AiInstruction.class, ListOptions.builder().build(), defaultSort())
                .filter(instruction -> instruction.getSpec() != null && instruction.getSpec().isEnabled())
                .filter(instruction -> bound(instruction.getSpec(), agentName))
                .sort(Comparator.comparingInt(Instructions::scopeOrder)
                        .thenComparing(Instructions::name));
    }

    public Mono<String> systemSections(String agentName) {
        return byAgent(agentName)
                .map(instruction -> instruction.getSpec().getContent())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    private static boolean bound(AiInstruction.Spec spec, String agentName) {
        var scope = spec.getScope();
        if (scope == AiInstruction.Scope.SHARED) {
            return true;
        }
        return scope == AiInstruction.Scope.AGENT
                && spec.getAgents() != null
                && spec.getAgents().contains(agentName);
    }

    private static int scopeOrder(AiInstruction instruction) {
        return instruction.getSpec().getScope() == AiInstruction.Scope.SHARED ? 0 : 1;
    }

    private static String name(AiInstruction instruction) {
        return instruction.getMetadata() == null
                ? ""
                : Objects.toString(instruction.getMetadata().getName(), "");
    }

    private static void requireNoBlankEntries(@Nullable List<String> values) {
        if (values != null && values.stream().anyMatch(value -> !StringUtils.hasText(value))) {
            throw invalid("agents 不能包含空值。");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, message);
    }
}