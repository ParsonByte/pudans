package cn.tolim.pudans.core.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.contract.ai.resource.AiSandboxProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.library.environment.EnvironmentVariables;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxCreateSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxErrors;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxExecResult;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxExecSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxHandle;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxLogEvent;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxProviderAdapter;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxState;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.runtime.AiAgentRuntimeRepository;
import cn.tolim.pudans.infra.r2dbc.ai.runtime.AiAgentRuntimeRepository.RuntimeRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AgentRuntimesTest {

    private static final String AGENT = "agent-1";

    private static final String USER = "user-1";

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    Teams teamService;

    @Mock
    EnvironmentVariables environmentService;

    @Mock
    AiAgentRuntimeRepository repository;

    @Mock
    cn.tolim.pudans.infra.r2dbc.ai.runtime.AiAgentSnapshotRepository snapshotRepository;

    @Mock
    SandboxProviderRegistry registry;

    @Mock
    RuntimeLogBuffer logBuffer;

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    private AgentRuntimes service;

    private AiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.getRuntime().setGatewayUrl("https://gateway.test/gateway");
        properties.getRuntime().setLockSeconds(60);
        properties.getRuntime().setLogPollSeconds(2);
        service = new AgentRuntimes(
                teamService,
                environmentService,
                repository,
                snapshotRepository,
                registry,
                logBuffer,
                redisTemplate,
                properties,
                JsonUtils.mapper());
    }

    @Test
    void startsASandboxAndInjectsTheGatewayEnvironment() {
        lockAcquired();
        var adapter = mock(SandboxProviderAdapter.class);
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.empty());
        when(registry.adapter(AiSandboxProvider.Provider.ALIYUN)).thenReturn(Mono.just(adapter));
        when(environmentService.resolveValues(AGENT)).thenReturn(Mono.just(Map.of("FOO", "bar")));
        when(repository.upsert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(adapter.create(any())).thenReturn(Mono.just(new SandboxHandle("sb-1", "https://sandbox.example.com")));
        when(repository.updateState(
                        eq(AGENT),
                        eq(SandboxState.RUNNING),
                        eq("sb-1"),
                        eq("https://sandbox.example.com"),
                        isNull(),
                        eq(true)))
                .thenReturn(Mono.just(record(SandboxState.RUNNING, "sb-1")));

        StepVerifier.create(service.start(AGENT, USER))
                .assertNext(view -> {
                    assertThat(view.agent()).isEqualTo(AGENT);
                    assertThat(view.provider()).isEqualTo("ALIYUN");
                    assertThat(view.state()).isEqualTo(SandboxState.RUNNING);
                    assertThat(view.sandboxId()).isEqualTo("sb-1");
                    assertThat(view.endpoint()).isEqualTo("https://sandbox.example.com");
                })
                .verifyComplete();

        var upsert = ArgumentCaptor.forClass(RuntimeRecord.class);
        verify(repository).upsert(upsert.capture());
        assertThat(upsert.getValue().state()).isEqualTo(SandboxState.CREATING);
        assertThat(upsert.getValue().provider()).isEqualTo(AiSandboxProvider.Provider.ALIYUN);
        assertThat(upsert.getValue().sandboxId()).isNull();

        var create = ArgumentCaptor.forClass(SandboxCreateSpec.class);
        verify(adapter).create(create.capture());
        assertThat(create.getValue().agentName()).isEqualTo(AGENT);
        assertThat(create.getValue().cpu()).isEqualTo(1.0);
        assertThat(create.getValue().memoryMb()).isEqualTo(1024);
        assertThat(create.getValue().timeoutSeconds()).isEqualTo(3600);
        assertThat(create.getValue().workdir()).isEqualTo("/workspace");
        assertThat(create.getValue().environment())
                .containsEntry("FOO", "bar")
                .containsEntry("PUDANS_AGENT_NAME", AGENT)
                .containsEntry("PUDANS_GATEWAY_URL", "https://gateway.test/gateway");
        verify(valueOperations).setIfAbsent(
                eq("ai:runtime:lock:" + AGENT), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void startIsIdempotentForAnAlreadyRunningSandbox() {
        lockAcquired();
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.just(record(SandboxState.RUNNING, "sb-1")));

        StepVerifier.create(service.start(AGENT, USER))
                .assertNext(view -> {
                    assertThat(view.state()).isEqualTo(SandboxState.RUNNING);
                    assertThat(view.sandboxId()).isEqualTo("sb-1");
                })
                .verifyComplete();

        verifyNoInteractions(registry);
        verifyNoInteractions(environmentService);
        verify(repository, never()).upsert(any());
        verify(repository, never()).updateState(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void rejectsStartWhenAnotherOperationHoldsTheLock() {
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.start(AGENT, USER))
                .expectErrorSatisfies(error -> assertApiError(error, SandboxErrors.LOCKED))
                .verify();

        verifyNoInteractions(repository);
        verifyNoInteractions(registry);
    }

    @Test
    void rejectsStartWhenTheAgentHasNoRuntimeProvider() {
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.NONE)));

        StepVerifier.create(service.start(AGENT, USER))
                .expectErrorSatisfies(error -> assertApiError(error, SandboxErrors.AGENT_NOT_CONFIGURED))
                .verify();

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(repository);
        verifyNoInteractions(registry);
    }

    @Test
    void stopsAndDestroysARunningSandbox() {
        lockAcquired();
        var adapter = mock(SandboxProviderAdapter.class);
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.just(record(SandboxState.RUNNING, "sb-1")));
        when(repository.updateState(
                        AGENT, SandboxState.STOPPING, "sb-1", "https://sandbox.example.com", null, false))
                .thenReturn(Mono.just(record(SandboxState.STOPPING, "sb-1")));
        when(registry.adapter(AiSandboxProvider.Provider.ALIYUN)).thenReturn(Mono.just(adapter));
        when(adapter.destroy("sb-1")).thenReturn(Mono.empty());
        when(repository.updateState(AGENT, SandboxState.STOPPED, null, null, null, false))
                .thenReturn(Mono.just(record(SandboxState.STOPPED, null)));

        StepVerifier.create(service.stop(AGENT, USER))
                .assertNext(view -> {
                    assertThat(view.state()).isEqualTo(SandboxState.STOPPED);
                    assertThat(view.sandboxId()).isNull();
                })
                .verifyComplete();

        verify(adapter).destroy("sb-1");
    }

    @Test
    void stopIsIdempotentForAnAlreadyStoppedRuntime() {
        lockAcquired();
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.just(record(SandboxState.STOPPED, null)));

        StepVerifier.create(service.stop(AGENT, USER))
                .assertNext(view -> assertThat(view.state()).isEqualTo(SandboxState.STOPPED))
                .verifyComplete();

        verifyNoInteractions(registry);
        verify(repository, never()).updateState(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void deletesTheSandboxAndTheRuntimeRecord() {
        lockAcquired();
        var adapter = mock(SandboxProviderAdapter.class);
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.just(record(SandboxState.RUNNING, "sb-1")));
        when(registry.adapter(AiSandboxProvider.Provider.ALIYUN)).thenReturn(Mono.just(adapter));
        when(adapter.destroy("sb-1")).thenReturn(Mono.empty());
        when(repository.delete(AGENT)).thenReturn(Mono.just(true));

        StepVerifier.create(service.delete(AGENT, USER)).verifyComplete();

        verify(adapter).destroy("sb-1");
        verify(repository).delete(AGENT);
        verify(logBuffer).remove(AGENT);
    }

    @Test
    void storesAWorkspaceSnapshot() {
        var adapter = mock(SandboxProviderAdapter.class);
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.just(record(SandboxState.RUNNING, "sb-1")));
        when(registry.adapter(AiSandboxProvider.Provider.ALIYUN)).thenReturn(Mono.just(adapter));
        when(environmentService.resolveValues(AGENT)).thenReturn(Mono.just(Map.of()));
        when(repository.markActive(AGENT)).thenReturn(Mono.empty());
        when(adapter.exec(eq("sb-1"), any()))
                .thenReturn(Mono.just(new SandboxExecResult(0, "", "", 5)));
        when(adapter.readFile(eq("sb-1"), anyString()))
                .thenReturn(Mono.just(new cn.tolim.pudans.core.ai.runtime.sandbox.SandboxFile(
                        "/tmp/snapshot.tgz", "base64", "aGVsbG8=", 5)));
        when(snapshotRepository.insert(eq(AGENT), eq("release"), eq(USER), any()))
                .thenReturn(Mono.just(new cn.tolim.pudans.infra.r2dbc.ai.runtime.AiAgentSnapshotRepository
                        .SnapshotRecord(7L, AGENT, "release", null, 5, USER, NOW)));

        StepVerifier.create(service.snapshot(AGENT, USER, "release", USER))
                .assertNext(view -> {
                    assertThat(view.id()).isEqualTo(7L);
                    assertThat(view.name()).isEqualTo("release");
                    assertThat(view.sizeBytes()).isEqualTo(5);
                })
                .verifyComplete();

        verify(adapter).readFile(eq("sb-1"), anyString());
    }

    @Test
    void restoresAWorkspaceSnapshot() {
        var adapter = mock(SandboxProviderAdapter.class);
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.just(record(SandboxState.RUNNING, "sb-1")));
        when(registry.adapter(AiSandboxProvider.Provider.ALIYUN)).thenReturn(Mono.just(adapter));
        when(snapshotRepository.find(7L, AGENT)).thenReturn(Mono.just(
                new cn.tolim.pudans.infra.r2dbc.ai.runtime.AiAgentSnapshotRepository.SnapshotRecord(
                        7L, AGENT, "release", "hello".getBytes(), 5, USER, NOW)));
        when(adapter.writeFile(eq("sb-1"), anyString(), any())).thenReturn(Mono.empty());
        when(environmentService.resolveValues(AGENT)).thenReturn(Mono.just(Map.of()));
        when(repository.markActive(AGENT)).thenReturn(Mono.empty());
        when(adapter.exec(eq("sb-1"), any()))
                .thenReturn(Mono.just(new SandboxExecResult(0, "", "", 5)));

        StepVerifier.create(service.restoreSnapshot(AGENT, USER, 7L)).verifyComplete();

        verify(adapter).writeFile(eq("sb-1"), anyString(), any());
        verify(adapter).exec(eq("sb-1"), any());
    }

    @Test
    void reportsTheSandboxStatus() {
        var adapter = mock(SandboxProviderAdapter.class);
        when(teamService.requireAgent(AGENT, USER))
                .thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT))
                .thenReturn(Mono.just(record(SandboxState.RUNNING, "sb-1")));
        when(registry.adapter(AiSandboxProvider.Provider.ALIYUN))
                .thenReturn(Mono.just(adapter));
        when(adapter.status("sb-1"))
                .thenReturn(Mono.just(
                        new cn.tolim.pudans.core.ai.runtime.sandbox.SandboxStatus(
                                SandboxState.RUNNING, "ok")));

        StepVerifier.create(service.status(AGENT, USER))
                .assertNext(status -> {
                    assertThat(status.state()).isEqualTo(SandboxState.RUNNING);
                    assertThat(status.message()).isEqualTo("ok");
                })
                .verifyComplete();
    }

    @Test
    void executesACommandInTheRunningSandbox() {
        var adapter = mock(SandboxProviderAdapter.class);
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.just(record(SandboxState.RUNNING, "sb-1")));
        when(registry.adapter(AiSandboxProvider.Provider.ALIYUN)).thenReturn(Mono.just(adapter));
        when(environmentService.resolveValues(AGENT)).thenReturn(Mono.just(Map.of("FOO", "bar")));
        when(adapter.exec(eq("sb-1"), any()))
                .thenReturn(Mono.just(new SandboxExecResult(0, "hello\n", "", 12)));
        when(repository.markActive(AGENT)).thenReturn(Mono.empty());

        StepVerifier.create(service.exec(AGENT, USER, "echo", List.of("hello"), null, 30))
                .assertNext(result -> {
                    assertThat(result.exitCode()).isZero();
                    assertThat(result.stdout()).isEqualTo("hello\n");
                })
                .verifyComplete();

        var spec = ArgumentCaptor.forClass(SandboxExecSpec.class);
        verify(adapter).exec(eq("sb-1"), spec.capture());
        assertThat(spec.getValue().command()).isEqualTo("echo");
        assertThat(spec.getValue().args()).containsExactly("hello");
        assertThat(spec.getValue().workdir()).isEqualTo("/workspace");
        assertThat(spec.getValue().timeoutSeconds()).isEqualTo(30);
        assertThat(spec.getValue().environment())
                .containsEntry("FOO", "bar")
                .containsEntry("PUDANS_AGENT_NAME", AGENT)
                .containsEntry("PUDANS_GATEWAY_URL", "https://gateway.test/gateway");
        verify(repository).markActive(AGENT);
        verify(logBuffer).appendOutput(AGENT, "stdout", "hello\n");
    }

    @Test
    void rejectsExecWhenTheSandboxIsNotRunning() {
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.just(record(SandboxState.STOPPED, null)));

        StepVerifier.create(service.exec(AGENT, USER, "echo", List.of("hello"), null, 30))
                .expectErrorSatisfies(error -> assertApiError(error, SandboxErrors.NOT_RUNNING))
                .verify();

        verifyNoInteractions(registry);
        verifyNoInteractions(environmentService);
    }

    @Test
    void rejectsAnUnknownFileEncodingBeforeTouchingTheAgent() {
        StepVerifier.create(service.writeFile(AGENT, USER, "/workspace/a.txt", "hex", "00ff"))
                .expectErrorSatisfies(error -> assertApiError(error, SandboxErrors.CONFIG_INVALID))
                .verify();

        verifyNoInteractions(teamService);
    }

    @Test
    void replaysBufferedLogsWithoutFollowing() {
        when(teamService.requireAgent(AGENT, USER)).thenReturn(Mono.just(agent(AiAgent.RuntimeProvider.ALIYUN)));
        when(repository.find(AGENT)).thenReturn(Mono.just(record(SandboxState.RUNNING, "sb-1")));
        when(logBuffer.snapshot(AGENT)).thenReturn(List.of(
                new SandboxLogEvent(NOW, "stdout", "first"),
                new SandboxLogEvent(NOW, "stderr", "second")));

        StepVerifier.create(service.logs(AGENT, USER, false, null))
                .expectNextMatches(event -> event.line().equals("first"))
                .expectNextMatches(event -> event.line().equals("second"))
                .verifyComplete();

        verifyNoInteractions(registry);
    }

    @Test
    void rejectsAnInvalidLogCursor() {
        StepVerifier.create(service.logs(AGENT, USER, false, "not-a-number"))
                .expectErrorSatisfies(error -> assertApiError(error, SandboxErrors.CONFIG_INVALID))
                .verify();

        verifyNoInteractions(teamService);
    }

    private void lockAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        doReturn(Flux.just(1L))
                .when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), anyList());
    }

    private static AiAgent agent(AiAgent.RuntimeProvider provider) {
        var agent = new AiAgent();
        agent.getSpec().setTeam("team-1");
        agent.getSpec().setOwnerUserId(USER);
        agent.getSpec().setDisplayName("Agent");
        agent.getSpec().getRuntime().setProvider(provider);
        return agent;
    }

    private static RuntimeRecord record(SandboxState state, @Nullable String sandboxId) {
        return new RuntimeRecord(
                AGENT,
                AiSandboxProvider.Provider.ALIYUN,
                sandboxId,
                state,
                "image-agent",
                null,
                1.0,
                1024,
                3600,
                "https://sandbox.example.com",
                null,
                "{}",
                NOW,
                NOW,
                NOW);
    }

    private static void assertApiError(Throwable error, String code) {
        assertThat(error).isInstanceOf(ApiException.class);
        assertThat(((ApiException) error).getCode()).isEqualTo(code);
    }
}