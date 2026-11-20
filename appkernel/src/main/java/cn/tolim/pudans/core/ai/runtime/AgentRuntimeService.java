package cn.tolim.pudans.core.ai.runtime;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiSandboxProvider;
import cn.tolim.pudans.core.ai.library.environment.EnvironmentVariableService;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxCreateSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxErrors;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxExecResult;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxExecSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxFile;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxFiles;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxLogEvent;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxProviderAdapter;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxState;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.runtime.AiAgentRuntimeRepository;
import cn.tolim.pudans.infra.r2dbc.ai.runtime.AiAgentRuntimeRepository.RuntimeRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class AgentRuntimeService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private static final int MAX_ERROR_LENGTH = 2000;

    private static final double MIN_CPU = 0.1;

    private static final double MAX_CPU = 16;

    private static final int MIN_MEMORY_MB = 128;

    private static final int MAX_MEMORY_MB = 65536;

    private static final int MIN_TIMEOUT_SECONDS = 60;

    private static final int MAX_TIMEOUT_SECONDS = 86400;

    private static final String LOCK_PREFIX = "ai:runtime:lock:";

    private static final RedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final AiTeamService teamService;

    private final EnvironmentVariableService environmentService;

    private final AiAgentRuntimeRepository repository;

    private final SandboxProviderRegistry registry;

    private final RuntimeLogBuffer logBuffer;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final AiProperties properties;

    private final ObjectMapper mapper;

    public AgentRuntimeService(
            AiTeamService teamService,
            EnvironmentVariableService environmentService,
            AiAgentRuntimeRepository repository,
            SandboxProviderRegistry registry,
            RuntimeLogBuffer logBuffer,
            ReactiveStringRedisTemplate redisTemplate,
            AiProperties properties,
            ObjectMapper mapper) {
        this.teamService = teamService;
        this.environmentService = environmentService;
        this.repository = repository;
        this.registry = registry;
        this.logBuffer = logBuffer;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.mapper = mapper;
    }

    public Flux<RuntimeView> list(String userId, long beforeId, int limit) {
        return repository.listBefore(beforeId, limit).concatMap(record -> teamService
                .requireAgent(record.agentName(), userId)
                .map(agent -> view(record))
                .onErrorResume(AccessDeniedException.class, error -> Mono.empty()));
    }

    public Mono<RuntimeView> get(String agentName, String userId) {
        return teamService.requireAgent(agentName, userId)
                .then(repository.find(agentName)
                        .switchIfEmpty(Mono.error(SandboxErrors.notFound(agentName))))
                .map(AgentRuntimeService::view);
    }

    public Mono<RuntimeView> start(String agentName, String userId) {
        return teamService.requireAgent(agentName, userId).flatMap(agent -> {
            var runtime = runtimeSpec(agentName, agent);
            return Mono.usingWhen(
                    acquireLock(agentName),
                    token -> doStart(agentName, agent, runtime),
                    token -> releaseLock(agentName, token));
        }).map(AgentRuntimeService::view);
    }

    public Mono<RuntimeView> stop(String agentName, String userId) {
        return teamService.requireAgent(agentName, userId)
                .flatMap(agent -> Mono.usingWhen(
                        acquireLock(agentName),
                        token -> doStop(agentName),
                        token -> releaseLock(agentName, token)))
                .map(AgentRuntimeService::view);
    }

    public Mono<RuntimeView> restart(String agentName, String userId) {
        return teamService.requireAgent(agentName, userId).flatMap(agent -> {
            var runtime = runtimeSpec(agentName, agent);
            return Mono.usingWhen(
                    acquireLock(agentName),
                    token -> doRestart(agentName, agent, runtime),
                    token -> releaseLock(agentName, token));
        }).map(AgentRuntimeService::view);
    }

    public Mono<Void> delete(String agentName, String userId) {
        return teamService.requireAgent(agentName, userId)
                .flatMap(agent -> Mono.usingWhen(
                        acquireLock(agentName),
                        token -> doDelete(agentName),
                        token -> releaseLock(agentName, token)));
    }

    public Mono<SandboxExecResult> exec(
            String agentName,
            String userId,
            String command,
            List<String> args,
            @Nullable String workdir,
            @Nullable Integer timeoutSeconds) {
        return Mono.defer(() -> {
            if (!StringUtils.hasText(command)) {
                return Mono.error(SandboxErrors.configInvalid("command 不能为空。"));
            }
            var arguments = args == null
                    ? List.<String>of()
                    : args.stream().filter(Objects::nonNull).toList();
            return teamService.requireAgent(agentName, userId).flatMap(agent -> requireRunning(agentName)
                    .flatMap(record -> {
                        var effectiveWorkdir = StringUtils.hasText(workdir)
                                ? workdir
                                : runtimeWorkdir(agent);
                        var timeout = effectiveTimeout(record, timeoutSeconds);
                        return environment(agentName)
                                .flatMap(environment -> adapter(record).flatMap(sandbox -> sandbox.exec(
                                        record.sandboxId(),
                                        new SandboxExecSpec(command, arguments, effectiveWorkdir, environment, timeout))))
                                .flatMap(result -> {
                                    logBuffer.appendOutput(agentName, "stdout", result.stdout());
                                    logBuffer.appendOutput(agentName, "stderr", result.stderr());
                                    return repository.markActive(agentName).thenReturn(result);
                                })
                                .onErrorMap(error -> SandboxErrors.execFailed(agentName, error));
                    }));
        });
    }

    public Mono<SandboxExecResult> execInternal(
            String agentName, String command, @Nullable String workdir, @Nullable Integer timeoutSeconds) {
        return Mono.defer(() -> {
            if (!StringUtils.hasText(command)) {
                return Mono.error(SandboxErrors.configInvalid("command 不能为空。"));
            }
            return requireRunning(agentName).flatMap(record -> {
                var timeout = effectiveTimeout(record, timeoutSeconds);
                return environment(agentName)
                        .flatMap(environment -> adapter(record)
                                .flatMap(sandbox -> sandbox.exec(
                                        record.sandboxId(),
                                        new SandboxExecSpec(command, List.of(), workdir, environment, timeout))))
                        .flatMap(result -> {
                            logBuffer.appendOutput(agentName, "stdout", result.stdout());
                            logBuffer.appendOutput(agentName, "stderr", result.stderr());
                            return repository.markActive(agentName).thenReturn(result);
                        })
                        .onErrorMap(error -> SandboxErrors.execFailed(agentName, error));
            });
        });
    }

    public Mono<SandboxFile> readFile(String agentName, String userId, String path) {
        return Mono.defer(() -> {
            if (!StringUtils.hasText(path)) {
                return Mono.error(SandboxErrors.configInvalid("path 不能为空。"));
            }
            return teamService.requireAgent(agentName, userId)
                    .then(requireRunning(agentName))
                    .flatMap(record -> adapter(record)
                            .flatMap(sandbox -> sandbox.readFile(record.sandboxId(), path)))
                    .flatMap(file -> repository.markActive(agentName).thenReturn(file))
                    .onErrorMap(error -> SandboxErrors.fileFailed(agentName, error));
        });
    }

    public Mono<Void> writeFile(
            String agentName, String userId, String path, String encoding, String content) {
        return Mono.defer(() -> {
            if (!StringUtils.hasText(path)) {
                return Mono.error(SandboxErrors.configInvalid("path 不能为空。"));
            }
            var bytes = SandboxFiles.decode(encoding, content);
            if (bytes.length > MAX_FILE_BYTES) {
                return Mono.error(SandboxErrors.fileTooLarge(bytes.length, MAX_FILE_BYTES));
            }
            return teamService.requireAgent(agentName, userId)
                    .then(requireRunning(agentName))
                    .flatMap(record -> adapter(record)
                            .flatMap(sandbox -> sandbox.writeFile(record.sandboxId(), path, bytes)))
                    .then(repository.markActive(agentName))
                    .onErrorMap(error -> SandboxErrors.fileFailed(agentName, error));
        });
    }

    public Flux<SandboxLogEvent> logs(
            String agentName, String userId, boolean follow, @Nullable String cursor) {
        return Flux.defer(() -> {
            var initialCursor = parseCursor(cursor);
            return teamService.requireAgent(agentName, userId)
                    .then(repository.find(agentName)
                            .switchIfEmpty(Mono.error(SandboxErrors.notFound(agentName))))
                    .flatMapMany(record -> {
                        var history = Flux.fromIterable(logBuffer.snapshot(agentName));
                        if (!follow
                                || record.state() != SandboxState.RUNNING
                                || !StringUtils.hasText(record.sandboxId())) {
                            return history;
                        }
                        return history.concatWith(liveLogs(agentName, record, initialCursor));
                    });
        });
    }

    public Flux<SandboxProviderRegistry.ProviderInfo> providers() {
        return registry.providers();
    }

    private Mono<RuntimeRecord> doStart(String agentName, AiAgent agent, RuntimeSpec runtime) {
        return repository.find(agentName).flatMap(existing -> {
            if (existing.state() == SandboxState.RUNNING && StringUtils.hasText(existing.sandboxId())) {
                return Mono.just(existing);
            }
            return registry.adapter(runtime.provider())
                    .flatMap(adapter -> destroyStale(agentName, existing).then(createRuntime(agentName, runtime, adapter)));
        }).switchIfEmpty(Mono.defer(() -> registry.adapter(runtime.provider())
                .flatMap(adapter -> createRuntime(agentName, runtime, adapter))));
    }

    private Mono<RuntimeRecord> doStop(String agentName) {
        return repository.find(agentName)
                .switchIfEmpty(Mono.error(SandboxErrors.notFound(agentName)))
                .flatMap(record -> {
                    if (!StringUtils.hasText(record.sandboxId())
                            && (record.state() == SandboxState.STOPPED
                                    || record.state() == SandboxState.DESTROYED)) {
                        return Mono.just(record);
                    }
                    if (!StringUtils.hasText(record.sandboxId())) {
                        return repository
                                .updateState(agentName, SandboxState.STOPPED, null, null, null, false)
                                .switchIfEmpty(Mono.just(record));
                    }
                    return repository
                            .updateState(
                                    agentName,
                                    SandboxState.STOPPING,
                                    record.sandboxId(),
                                    record.endpoint(),
                                    null,
                                    false)
                            .switchIfEmpty(Mono.error(SandboxErrors.notFound(agentName)))
                            .flatMap(stopping -> registry
                                    .adapter(record.provider())
                                    .flatMap(adapter -> adapter.destroy(record.sandboxId()))
                                    .then(repository.updateState(
                                            agentName, SandboxState.STOPPED, null, null, null, false))
                                    .switchIfEmpty(Mono.error(SandboxErrors.notFound(agentName))))
                            .onErrorResume(error -> markFailed(agentName, error)
                                    .then(Mono.error(SandboxErrors.destroyFailed(agentName, error))));
                });
    }

    private Mono<RuntimeRecord> doRestart(String agentName, AiAgent agent, RuntimeSpec runtime) {
        return repository.find(agentName)
                .flatMap(existing -> destroyStale(agentName, existing))
                .then(registry.adapter(runtime.provider())
                        .flatMap(adapter -> createRuntime(agentName, runtime, adapter)));
    }

    private Mono<Void> doDelete(String agentName) {
        return repository.find(agentName).flatMap(record -> {
            var cleanup = StringUtils.hasText(record.sandboxId())
                    ? registry.adapter(record.provider())
                            .flatMap(adapter -> adapter.destroy(record.sandboxId()))
                    : Mono.<Void>empty();
            return cleanup
                    .then(repository.delete(agentName))
                    .doOnNext(deleted -> logBuffer.remove(agentName))
                    .then();
        });
    }

    private Mono<RuntimeRecord> createRuntime(
            String agentName, RuntimeSpec runtime, SandboxProviderAdapter adapter) {
        return environment(agentName).flatMap(environment -> {
            var create = new SandboxCreateSpec(
                    agentName,
                    runtime.image(),
                    runtime.entrypoint(),
                    runtime.cpu(),
                    runtime.memoryMb(),
                    runtime.timeoutSeconds(),
                    runtime.networkEnabled(),
                    runtime.workdir(),
                    environment);
            var creating = new RuntimeRecord(
                    agentName,
                    runtime.provider(),
                    null,
                    SandboxState.CREATING,
                    runtime.image(),
                    runtime.entrypoint(),
                    runtime.cpu(),
                    runtime.memoryMb(),
                    runtime.timeoutSeconds(),
                    null,
                    null,
                    runtime.configJson(),
                    null,
                    null,
                    null);
            return repository.upsert(creating)
                    .flatMap(saved -> adapter.create(create)
                            .flatMap(handle -> repository
                                    .updateState(
                                            agentName,
                                            SandboxState.RUNNING,
                                            handle.sandboxId(),
                                            handle.endpoint(),
                                            null,
                                            true)
                                    .switchIfEmpty(Mono.defer(() -> adapter.destroy(handle.sandboxId())
                                            .then(Mono.error(SandboxErrors.notFound(agentName))))))
                            .onErrorResume(error -> markFailed(agentName, error)
                                    .then(Mono.error(SandboxErrors.createFailed(agentName, error)))));
        });
    }

    private Mono<Void> destroyStale(String agentName, RuntimeRecord record) {
        if (!StringUtils.hasText(record.sandboxId())) {
            return Mono.empty();
        }
        return registry
                .adapter(record.provider())
                .flatMap(adapter -> adapter.destroy(record.sandboxId()))
                .onErrorMap(error -> SandboxErrors.destroyFailed(agentName, error));
    }

    private Mono<RuntimeRecord> requireRunning(String agentName) {
        return repository.find(agentName)
                .switchIfEmpty(Mono.error(SandboxErrors.notFound(agentName)))
                .flatMap(record -> record.state() == SandboxState.RUNNING
                                && StringUtils.hasText(record.sandboxId())
                        ? Mono.just(record)
                        : Mono.error(SandboxErrors.notRunning(agentName)));
    }

    private Flux<SandboxLogEvent> liveLogs(
            String agentName, RuntimeRecord record, long startCursor) {
        var cursor = new AtomicLong(startCursor);
        var interval = Duration.ofSeconds(Math.max(1, properties.getRuntime().getLogPollSeconds()));
        return registry
                .adapter(record.provider())
                .flatMapMany(adapter -> Flux.interval(Duration.ZERO, interval)
                        .concatMap(tick -> adapter
                                .logs(record.sandboxId(), Long.toString(cursor.get()))
                                .doOnNext(event -> {
                                    logBuffer.append(agentName, event);
                                    cursor.addAndGet(
                                            event.line().getBytes(StandardCharsets.UTF_8).length + 1L);
                                })
                                .onErrorResume(error -> {
                                    log.warn("读取 Agent {} 的运行时日志失败。", agentName);
                                    return Flux.empty();
                                })));
    }

    private Mono<SandboxProviderAdapter> adapter(RuntimeRecord record) {
        return registry.adapter(record.provider());
    }

    private Mono<Map<String, String>> environment(String agentName) {
        return environmentService.resolveValues(agentName).flatMap(values -> {
            var gatewayUrl = properties.getRuntime().getGatewayUrl();
            if (!StringUtils.hasText(gatewayUrl)) {
                return Mono.error(SandboxErrors.configInvalid("未配置 pudans.ai.runtime.gateway-url。"));
            }
            var environment = new LinkedHashMap<>(values);
            environment.put("PUDANS_AGENT_NAME", agentName);
            environment.put("PUDANS_GATEWAY_URL", gatewayUrl);
            return Mono.just(Map.copyOf(environment));
        });
    }

    private Mono<String> acquireLock(String agentName) {
        var token = UUID.randomUUID().toString();
        return redisTemplate
                .opsForValue()
                .setIfAbsent(
                        LOCK_PREFIX + agentName,
                        token,
                        Duration.ofSeconds(Math.max(1, properties.getRuntime().getLockSeconds())))
                .flatMap(acquired -> Boolean.TRUE.equals(acquired)
                        ? Mono.just(token)
                        : Mono.error(SandboxErrors.locked(agentName)))
                .onErrorMap(error -> error instanceof ApiException
                        ? error
                        : SandboxErrors.lockFailed(agentName, error));
    }

    private Mono<Void> releaseLock(String agentName, String token) {
        return redisTemplate
                .execute(RELEASE_LOCK, List.of(LOCK_PREFIX + agentName), List.of(token))
                .then()
                .onErrorResume(error -> {
                    log.warn("释放 Agent {} 的运行时锁失败。", agentName);
                    return Mono.empty();
                });
    }

    private RuntimeSpec runtimeSpec(String agentName, AiAgent agent) {
        var runtime = agent.getSpec() == null ? null : agent.getSpec().getRuntime();
        if (runtime == null
                || runtime.getProvider() == null
                || runtime.getProvider() == AiAgent.RuntimeProvider.NONE) {
            throw SandboxErrors.agentNotConfigured(agentName);
        }
        var cpu = runtime.getCpu() == null ? 1.0 : runtime.getCpu();
        if (cpu < MIN_CPU || cpu > MAX_CPU) {
            throw SandboxErrors.configInvalid("Agent cpu 必须在 " + MIN_CPU + " 到 " + MAX_CPU + " 之间。");
        }
        var memoryMb = runtime.getMemoryMb() == null ? 1024 : runtime.getMemoryMb();
        if (memoryMb < MIN_MEMORY_MB || memoryMb > MAX_MEMORY_MB) {
            throw SandboxErrors.configInvalid(
                    "Agent memoryMb 必须在 " + MIN_MEMORY_MB + " 到 " + MAX_MEMORY_MB + " 之间。");
        }
        var timeout = runtime.getTimeoutSeconds() == null ? 3600 : runtime.getTimeoutSeconds();
        if (timeout < MIN_TIMEOUT_SECONDS || timeout > MAX_TIMEOUT_SECONDS) {
            throw SandboxErrors.configInvalid(
                    "Agent timeoutSeconds 必须在 " + MIN_TIMEOUT_SECONDS + " 到 " + MAX_TIMEOUT_SECONDS + " 之间。");
        }
        var provider = AiSandboxProvider.Provider.valueOf(runtime.getProvider().name());
        var workdir = StringUtils.hasText(runtime.getWorkdir()) ? runtime.getWorkdir() : "/workspace";
        var image = StringUtils.hasText(runtime.getImage()) ? runtime.getImage() : null;
        var entrypoint = StringUtils.hasText(runtime.getEntrypoint()) ? runtime.getEntrypoint() : null;
        var config = new LinkedHashMap<String, Object>();
        config.put("provider", provider.name());
        if (image != null) {
            config.put("image", image);
        }
        if (entrypoint != null) {
            config.put("entrypoint", entrypoint);
        }
        config.put("cpu", cpu);
        config.put("memoryMb", memoryMb);
        config.put("timeoutSeconds", timeout);
        config.put("networkEnabled", runtime.isNetworkEnabled());
        config.put("workdir", workdir);
        if (agent.getSpec() != null && agent.getSpec().getEnvironmentVariables() != null) {
            config.put("environmentVariables", agent.getSpec().getEnvironmentVariables());
        }
        return new RuntimeSpec(
                provider,
                image,
                entrypoint,
                cpu,
                memoryMb,
                timeout,
                runtime.isNetworkEnabled(),
                workdir,
                writeConfig(config));
    }

    private String writeConfig(Map<String, Object> config) {
        try {
            return mapper.writeValueAsString(config);
        } catch (JsonProcessingException error) {
            throw SandboxErrors.configInvalid("Agent runtime 配置无法序列化。");
        }
    }

    private Mono<RuntimeRecord> markFailed(String agentName, Throwable error) {
        return repository
                .updateState(agentName, SandboxState.FAILED, null, null, errorMessage(error), false)
                .switchIfEmpty(Mono.defer(() -> repository.find(agentName)));
    }

    private static int effectiveTimeout(RuntimeRecord record, @Nullable Integer requested) {
        var configured = record.timeoutSeconds() == null ? 3600 : record.timeoutSeconds();
        var timeout = requested == null ? configured : requested;
        if (timeout <= 0) {
            throw SandboxErrors.configInvalid("timeoutSeconds 必须大于 0。");
        }
        return Math.min(timeout, configured);
    }

    private static String runtimeWorkdir(AiAgent agent) {
        var runtime = agent.getSpec() == null ? null : agent.getSpec().getRuntime();
        return runtime == null || !StringUtils.hasText(runtime.getWorkdir())
                ? "/workspace"
                : runtime.getWorkdir();
    }

    private static long parseCursor(@Nullable String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(cursor.strip()));
        } catch (NumberFormatException error) {
            throw SandboxErrors.configInvalid("日志游标必须是字节偏移量。");
        }
    }

    private static String errorMessage(Throwable error) {
        var message = error instanceof ApiException apiException
                ? apiException.getReason()
                : error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error.getClass().getSimpleName();
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private static RuntimeView view(RuntimeRecord record) {
        return new RuntimeView(
                record.agentName(),
                record.provider().name(),
                record.state(),
                record.sandboxId(),
                record.image(),
                record.entrypoint(),
                record.cpu(),
                record.memoryMb(),
                record.timeoutSeconds(),
                record.endpoint(),
                record.lastError(),
                record.createdAt(),
                record.updatedAt(),
                record.lastActiveAt());
    }

    private record RuntimeSpec(
            AiSandboxProvider.Provider provider,
            @Nullable String image,
            @Nullable String entrypoint,
            double cpu,
            int memoryMb,
            int timeoutSeconds,
            boolean networkEnabled,
            String workdir,
            String configJson) {}

    public record RuntimeView(
            String agent,
            String provider,
            SandboxState state,
            @Nullable String sandboxId,
            @Nullable String image,
            @Nullable String entrypoint,
            @Nullable Double cpu,
            @Nullable Integer memoryMb,
            @Nullable Integer timeoutSeconds,
            @Nullable String endpoint,
            @Nullable String lastError,
            @Nullable Instant createdAt,
            @Nullable Instant updatedAt,
            @Nullable Instant lastActiveAt) {}
}