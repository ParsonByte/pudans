package cn.tolim.pudans.core.ai.memory.prompt;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.ai.resource.AiMemoryPrompt;
import cn.tolim.pudans.contract.ai.resource.AiMemoryPromptBinding;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.ResolvedMemoryPrompt;
import cn.tolim.pudans.core.ai.memory.asset.MemoryAssetNames;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryPromptLogRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MemoryPromptSettings {

    public static final String CODE_NOT_FOUND = "ai-memory-prompt-not-found";

    public static final String CODE_INVALID = "ai-memory-prompt-invalid";

    public static final String CODE_CONFLICT = "ai-memory-prompt-conflict";

    private static final int MAX_LOG_LIMIT = 200;

    private final ReactiveExtensionClient client;

    private final MemoryPromptResolver resolver;

    private final AiMemoryPromptLogRepository logRepository;

    private final Teams teams;

    public Mono<ResolvedMemoryPrompt> settings(
            AiMemoryPrompt.Layer layer, @Nullable String team, @Nullable String agent) {
        return loadBindingsAndPrompts()
                .map(sources -> resolver.resolve(layer, team, agent, sources.bindings(), sources.prompts()));
    }

    public Mono<List<ResolvedMemoryPrompt>> settings(@Nullable String team, @Nullable String agent) {
        return loadBindingsAndPrompts()
                .flatMap(sources -> Flux.fromArray(AiMemoryPrompt.Layer.values())
                        .map(layer -> resolver.resolve(layer, team, agent, sources.bindings(), sources.prompts()))
                        .collectList());
    }

    public Mono<MemoryPromptLog> set(String actor, SetCommand command) {
        if (!StringUtils.hasText(command.name())
                || command.layer() == null
                || command.targetType() == null
                || command.action() == null) {
            return Mono.error(invalid("name、layer、targetType 和 action 不能为空。"));
        }
        var target = normalizeTarget(command.targetType(), command.team(), command.agent());
        if (target.isError()) {
            return Mono.error(target.error());
        }
        var layer = command.layer();
        var name = command.name().strip();
        return validateTarget(target.value())
                .then(Mono.defer(() -> command.action() == MemoryPromptLog.Action.APPLY
                        ? apply(actor, name, layer, target.value())
                        : clear(actor, name, layer, target.value())));
    }

    public Flux<MemoryPromptLog> logs(
            @Nullable String promptId, @Nullable Instant from, @Nullable Instant to, long beforeId, int limit) {
        if (limit < 1 || limit > MAX_LOG_LIMIT) {
            return Flux.error(invalid("limit 必须在 1 到 200 之间。"));
        }
        return logRepository.list(
                StringUtils.hasText(promptId) ? promptId.strip() : null, from, to, beforeId, limit);
    }

    private Mono<MemoryPromptLog> apply(
            String actor, String name, AiMemoryPrompt.Layer layer, Target target) {
        return client
                .get(AiMemoryPrompt.class, name)
                .switchIfEmpty(Mono.error(notFound()))
                .flatMap(prompt -> {
                    if (prompt.getSpec() == null || prompt.getSpec().getLayer() != layer) {
                        return Mono.error(invalid("Prompt 层与请求层不一致。"));
                    }
                    if (prompt.getSpec().getStatus() != AiMemoryPrompt.PromptStatus.ACTIVE
                            || !StringUtils.hasText(prompt.getSpec().getPrompt())) {
                        return Mono.error(invalid("Prompt 未激活或内容为空。"));
                    }
                    var binding = new AiMemoryPromptBinding.Spec();
                    binding.setTargetType(target.targetType());
                    binding.setTeam(target.team());
                    binding.setAgent(target.agent());
                    binding.setLayer(layer);
                    binding.setPrompt(name);
                    binding.setUpdatedBy(actor);
                    var bindingName = MemoryAssetNames.bindingName(
                            layer.name(), target.targetType().name(), target.team(), target.agent());
                    return upsertBinding(bindingName, binding)
                            .then(logRepository.insert(new MemoryPromptLog(
                                    null,
                                    name,
                                    prompt.getSpec().getVersion(),
                                    MemoryPromptLog.Action.APPLY,
                                    target.targetType(),
                                    target.team(),
                                    target.agent(),
                                    layer,
                                    actor,
                                    null)));
                });
    }

    private Mono<MemoryPromptLog> clear(
            String actor, String name, AiMemoryPrompt.Layer layer, Target target) {
        var bindingName =
                MemoryAssetNames.bindingName(layer.name(), target.targetType().name(), target.team(), target.agent());
        return client
                .get(AiMemoryPromptBinding.class, bindingName)
                .flatMap(binding -> {
                    var previous = binding.getSpec() == null ? null : binding.getSpec().getPrompt();
                    return client.delete(binding).thenReturn(previous);
                })
                .defaultIfEmpty("")
                .flatMap(previous -> {
                    var promptId = StringUtils.hasText(previous) ? previous : name;
                    return promptVersion(promptId)
                            .map(version -> new MemoryPromptLog(
                                    null,
                                    promptId,
                                    version,
                                    MemoryPromptLog.Action.CLEAR,
                                    target.targetType(),
                                    target.team(),
                                    target.agent(),
                                    layer,
                                    actor,
                                    null))
                            .defaultIfEmpty(new MemoryPromptLog(
                                    null,
                                    promptId,
                                    null,
                                    MemoryPromptLog.Action.CLEAR,
                                    target.targetType(),
                                    target.team(),
                                    target.agent(),
                                    layer,
                                    actor,
                                    null));
                })
                .flatMap(logRepository::insert);
    }

    private Mono<Integer> promptVersion(String promptId) {
        if (!StringUtils.hasText(promptId)) {
            return Mono.empty();
        }
        return client.get(AiMemoryPrompt.class, promptId)
                .flatMap(prompt -> Mono.justOrEmpty(
                        prompt.getSpec() == null ? null : prompt.getSpec().getVersion()));
    }

    private Mono<AiMemoryPromptBinding> upsertBinding(
            String name, AiMemoryPromptBinding.Spec spec) {
        return client
                .get(AiMemoryPromptBinding.class, name)
                .flatMap(existing -> {
                    existing.setSpec(spec);
                    return client.update(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    var binding = new AiMemoryPromptBinding();
                    binding.setSpec(spec);
                    var metadata = new Metadata();
                    metadata.setName(name);
                    binding.setMetadata(metadata);
                    return client
                            .create(binding)
                            .onErrorMap(DuplicateNameException.class, error -> conflict("Prompt 绑定名称冲突。"))
                            .onErrorMap(
                                    DataIntegrityViolationException.class,
                                    error -> conflict("Prompt 绑定名称冲突。"));
                }));
    }

    private Mono<PromptSources> loadBindingsAndPrompts() {
        return Mono.zip(
                        client.listAll(AiMemoryPromptBinding.class, ListOptions.builder().build(), defaultSort())
                                .collectList(),
                        client.listAll(AiMemoryPrompt.class, ListOptions.builder().build(), defaultSort())
                                .collectList())
                .map(tuple -> new PromptSources(tuple.getT1(), tuple.getT2()));
    }

    private Mono<Void> validateTarget(Target target) {
        if (target.targetType() == AiMemoryPromptBinding.TargetType.INSTANCE) {
            return Mono.empty();
        }
        if (target.targetType() == AiMemoryPromptBinding.TargetType.TEAM
                || target.targetType() == AiMemoryPromptBinding.TargetType.AGENT) {
            return teams
                    .getTeam(target.team())
                    .switchIfEmpty(Mono.error(invalid("team 不存在。")))
                    .flatMap(team -> target.targetType() == AiMemoryPromptBinding.TargetType.TEAM
                            ? Mono.empty()
                            : teams
                                    .getAgent(target.agent())
                                    .filter(agent -> agent.getSpec() != null
                                            && target.team().equals(agent.getSpec().getTeam()))
                                    .switchIfEmpty(Mono.error(invalid("agent 不存在或不属于该团队。")))
                                    .then());
        }
        return Mono.empty();
    }

    private static Validation<Target> normalizeTarget(
            AiMemoryPromptBinding.TargetType targetType,
            @Nullable String team,
            @Nullable String agent) {
        return switch (targetType) {
            case INSTANCE -> Validation.ok(new Target(targetType, null, null));
            case TEAM -> StringUtils.hasText(team)
                    ? Validation.ok(new Target(targetType, team.strip(), null))
                    : Validation.error(invalid("team 不能为空。"));
            case AGENT -> StringUtils.hasText(team) && StringUtils.hasText(agent)
                    ? Validation.ok(new Target(targetType, team.strip(), agent.strip()))
                    : Validation.error(invalid("AGENT 目标需要 team 与 agent。"));
        };
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, "记忆 Prompt 不存在。");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, CODE_CONFLICT, message);
    }

    private record Target(
            AiMemoryPromptBinding.TargetType targetType, @Nullable String team, @Nullable String agent) {}

    private record PromptSources(List<AiMemoryPromptBinding> bindings, List<AiMemoryPrompt> prompts) {}

    private record Validation<T>(@Nullable T value, @Nullable ApiException error) {

        static <T> Validation<T> ok(T value) {
            return new Validation<>(value, null);
        }

        static <T> Validation<T> error(ApiException error) {
            return new Validation<>(null, error);
        }

        boolean isError() {
            return error != null;
        }
    }

    public record SetCommand(
            @Nullable String name,
            AiMemoryPrompt.@Nullable Layer layer,
            AiMemoryPromptBinding.@Nullable TargetType targetType,
            @Nullable String team,
            @Nullable String agent,
            MemoryPromptLog.@Nullable Action action) {}
}