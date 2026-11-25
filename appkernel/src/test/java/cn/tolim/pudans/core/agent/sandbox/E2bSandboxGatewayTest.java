package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.extensions.sandbox.e2b.E2bSandboxClient;
import io.agentscope.extensions.sandbox.e2b.E2bSandboxClientOptions;
import io.agentscope.extensions.sandbox.e2b.E2bSandboxState;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class E2bSandboxGatewayTest {

    private static final String WORKSPACE_ROOT = "/home/user";

    private static final String PROVIDER_STATE = "{\"sandboxId\":\"e2b-sandbox-1\"}";

    @Mock
    E2bSandboxClient client;

    @Mock
    Sandbox sandbox;

    private SandboxProperties properties;

    private E2bSandboxGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new SandboxProperties();
        properties.setEnabled(true);
        properties.setProvider("e2b");
        properties.setDefaultTtl(Duration.ofMinutes(30));
        properties.getE2b().setApiKey("e2b-key");
        properties.getE2b().setTemplate("pudans-workspace");
        properties.getE2b().setWorkspaceRoot(WORKSPACE_ROOT);
        gateway = new E2bSandboxGateway(properties, options -> client);
    }

    @Test
    void reportsUnconfiguredUntilTheE2bProviderIsEnabled() {
        properties.setEnabled(false);

        StepVerifier.create(gateway.create(command()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(SandboxGateway.Failure.class, failure ->
                                assertThat(failure.code())
                                        .isEqualTo("sandbox-provider-not-configured")))
                .verify();

        verify(client, never()).create(any(), any(), any());
    }

    @Test
    void createsARealE2bSandboxWithTheConfiguredTemplateAndWorkspace() throws Exception {
        var state = new E2bSandboxState();
        state.setSandboxId("e2b-sandbox-1");
        state.setWorkspaceRoot(WORKSPACE_ROOT);
        when(client.create(any(), any(), any())).thenReturn(sandbox);
        when(sandbox.getState()).thenReturn(state);
        when(client.serializeState(state)).thenReturn(PROVIDER_STATE);

        StepVerifier.create(gateway.create(command()))
                .assertNext(created -> {
                    assertThat(created.providerSandboxId()).isEqualTo("e2b-sandbox-1");
                    assertThat(created.providerState()).isEqualTo(PROVIDER_STATE);
                    assertThat(created.workspaceRoot()).isEqualTo(WORKSPACE_ROOT);
                })
                .verifyComplete();

        verify(sandbox).start();
        var options = ArgumentCaptor.forClass(E2bSandboxClientOptions.class);
        verify(client).create(any(), any(), options.capture());
        assertThat(options.getValue().getApiKey()).isEqualTo("e2b-key");
        assertThat(options.getValue().getTemplateId()).isEqualTo("request-template");
        assertThat(options.getValue().getWorkspaceRoot()).isEqualTo(WORKSPACE_ROOT);
        assertThat(options.getValue().getSandboxTimeoutSeconds()).isEqualTo(1_800);
    }

    @Test
    void executesInsideTheE2bWorkspaceAndPreservesProviderExitCodes() throws Exception {
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
                eq("cd '/home/user/project' && (printf ok)"),
                eq(20));
    }

    private void givenActiveSandbox() throws Exception {
        var state = new E2bSandboxState();
        state.setSandboxId("e2b-sandbox-1");
        state.setWorkspaceRoot(WORKSPACE_ROOT);
        when(client.create(any(), any(), any())).thenReturn(sandbox);
        when(sandbox.getState()).thenReturn(state);
        when(client.serializeState(state)).thenReturn(PROVIDER_STATE);
    }

    private static SandboxGateway.CreateCommand command() {
        return new SandboxGateway.CreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "request-template",
                null);
    }
}
