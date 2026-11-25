package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.extensions.sandbox.agentrun.AgentRunSandboxClient;
import io.agentscope.extensions.sandbox.agentrun.AgentRunSandboxClientOptions;
import io.agentscope.extensions.sandbox.agentrun.AgentRunSandboxState;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AgentRunSandboxGatewayTest {

    private static final String WORKSPACE_ROOT = "/home/agentscope/workspace";

    private static final String PROVIDER_STATE = "{\"sandboxId\":\"provider-1\"}";

    @Mock
    AgentRunSandboxClient client;

    @Mock
    Sandbox sandbox;

    private SandboxProperties properties;

    private AgentRunSandboxGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new SandboxProperties();
        properties.setEnabled(true);
        properties.setApiKey("api-key");
        properties.setAccountId("account-1");
        properties.setRegion("cn-shanghai");
        properties.setTemplate("default-template");
        properties.setMcpServerUrl("https://mcp.example.com");
        properties.setWorkspaceRoot(WORKSPACE_ROOT);
        gateway = new AgentRunSandboxGateway(properties, options -> client);
    }

    @Test
    void reportsUnconfiguredWhenRequiredProviderSettingsAreMissing() {
        properties.setEnabled(false);

        StepVerifier.create(gateway.create(command()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(SandboxGateway.Failure.class, failure -> {
                            assertThat(failure.code()).isEqualTo("sandbox-provider-not-configured");
                        }))
                .verify();

        verify(client, never()).create(any(), any(), any());
    }

    @Test
    void createsAndSerializesARealProviderSandbox() throws Exception {
        var state = new AgentRunSandboxState();
        state.setSandboxId("provider-1");
        state.setWorkspaceRoot(WORKSPACE_ROOT);
        when(client.create(any(), any(), any())).thenReturn(sandbox);
        when(sandbox.getState()).thenReturn(state);
        when(client.serializeState(state)).thenReturn(PROVIDER_STATE);

        StepVerifier.create(gateway.create(command()))
                .assertNext(created -> {
                    assertThat(created.providerSandboxId()).isEqualTo("provider-1");
                    assertThat(created.providerState()).isEqualTo(PROVIDER_STATE);
                    assertThat(created.workspaceRoot()).isEqualTo(WORKSPACE_ROOT);
                })
                .verifyComplete();

        verify(sandbox).start();
        var options = ArgumentCaptor.forClass(AgentRunSandboxClientOptions.class);
        verify(client).create(any(), any(), options.capture());
        assertThat(options.getValue().getTemplateName()).isEqualTo("working-template");
        assertThat(options.getValue().getRegion()).isEqualTo("cn-beijing");
        assertThat(options.getValue().getWorkspaceRoot()).isEqualTo(WORKSPACE_ROOT);
    }

    @Test
    void shutsDownAnUnfinishedProviderSandboxWhenCreationFails() throws Exception {
        when(client.create(any(), any(), any())).thenReturn(sandbox);
        doThrow(new IllegalStateException("start failed")).when(sandbox).start();

        StepVerifier.create(gateway.create(command()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(SandboxGateway.Failure.class, failure -> {
                            assertThat(failure.code()).isEqualTo("sandbox-provider-failed");
                        }))
                .verify();

        verify(sandbox).shutdown();
    }

    @Test
    void executesInTheProviderWorkspaceAndReturnsTheNonZeroExitResult() throws Exception {
        givenActiveSandbox();
        when(sandbox.isRunning()).thenReturn(true);
        when(sandbox.exec(isNull(), anyString(), eq(20)))
                .thenThrow(new SandboxException.ExecException(2, "partial", "failed"));

        var command = command();
        var created = gateway.create(command).block();
        assertThat(created).isNotNull();
        StepVerifier.create(gateway.exec(new SandboxGateway.ExecCommand(
                        command.sandboxId(),
                        PROVIDER_STATE,
                        "printf ok",
                        WORKSPACE_ROOT + "/project",
                        20)))
                .assertNext(result -> {
                    assertThat(result.exitCode()).isEqualTo(2);
                    assertThat(result.stdout()).isEqualTo("partial");
                    assertThat(result.stderr()).isEqualTo("failed");
                })
                .verifyComplete();

        verify(sandbox).exec(
                isNull(),
                eq("cd '/home/agentscope/workspace/project' && (printf ok)"),
                eq(20));
    }

    @Test
    void preservesProviderExecutionTimeoutAsAStableErrorType() throws Exception {
        givenActiveSandbox();
        when(sandbox.isRunning()).thenReturn(true);
        when(sandbox.exec(isNull(), anyString(), eq(10)))
                .thenThrow(new SandboxException.ExecTimeoutException("pwd", 10));

        var command = command();
        var created = gateway.create(command).block();
        assertThat(created).isNotNull();
        StepVerifier.create(gateway.exec(new SandboxGateway.ExecCommand(
                        command.sandboxId(),
                        PROVIDER_STATE,
                        "pwd",
                        WORKSPACE_ROOT,
                        10)))
                .expectError(SandboxGateway.ExecTimeout.class)
                .verify();
    }

    @Test
    void mapsUnexpectedProviderFailuresToTheStableGatewayError() throws Exception {
        givenActiveSandbox();
        when(sandbox.isRunning()).thenReturn(true);
        when(sandbox.exec(isNull(), anyString(), eq(10)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        var command = command();
        var created = gateway.create(command).block();
        assertThat(created).isNotNull();
        StepVerifier.create(gateway.exec(new SandboxGateway.ExecCommand(
                        command.sandboxId(),
                        PROVIDER_STATE,
                        "pwd",
                        WORKSPACE_ROOT,
                        10)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(SandboxGateway.Failure.class, failure -> {
                            assertThat(failure.code()).isEqualTo("sandbox-provider-failed");
                        }))
                .verify();
    }

    private void givenActiveSandbox() throws Exception {
        var state = new AgentRunSandboxState();
        state.setSandboxId("provider-1");
        state.setWorkspaceRoot(WORKSPACE_ROOT);
        when(client.create(any(), any(), any())).thenReturn(sandbox);
        when(sandbox.getState()).thenReturn(state);
        when(client.serializeState(state)).thenReturn(PROVIDER_STATE);
    }

    private static SandboxGateway.CreateCommand command() {
        return new SandboxGateway.CreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "working-template",
                "cn-beijing");
    }
}
