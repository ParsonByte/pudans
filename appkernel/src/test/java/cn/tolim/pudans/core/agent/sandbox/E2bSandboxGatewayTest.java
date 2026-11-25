package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class E2bSandboxGatewayTest {

    private static final String WORKSPACE_ROOT = "/home/user";

    private static final String PROVIDER_STATE = "{\"sandboxId\":\"e2b-sandbox-1\"}";

    @Mock
    SandboxClient client;

    @Mock
    SandboxSession session;

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
        gateway = new E2bSandboxGateway(properties, client);
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
        when(client.create(WORKSPACE_ROOT, "request-template", null)).thenReturn(session);
        when(session.providerSandboxId()).thenReturn("e2b-sandbox-1");
        when(session.providerState()).thenReturn(PROVIDER_STATE);
        when(session.workspaceRoot()).thenReturn(WORKSPACE_ROOT);

        StepVerifier.create(gateway.create(command()))
                .assertNext(created -> {
                    assertThat(created.providerSandboxId()).isEqualTo("e2b-sandbox-1");
                    assertThat(created.providerState()).isEqualTo(PROVIDER_STATE);
                    assertThat(created.workspaceRoot()).isEqualTo(WORKSPACE_ROOT);
                })
                .verifyComplete();

        verify(session).start();
    }

    @Test
    void executesInsideTheE2bWorkspaceAndPreservesProviderExitCodes() throws Exception {
        givenActiveSandbox();
        when(session.isRunning()).thenReturn(true);
        when(session.exec(
                        isNull(),
                        eq("cd '/home/user/project' && (printf ok)"),
                        eq(20)))
                .thenReturn(new SandboxGateway.Execution(2, "partial", "failed", false));

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
    }

    private void givenActiveSandbox() throws Exception {
        when(client.create(any(), any(), any())).thenReturn(session);
        when(session.providerSandboxId()).thenReturn("e2b-sandbox-1");
        when(session.providerState()).thenReturn(PROVIDER_STATE);
        when(session.workspaceRoot()).thenReturn(WORKSPACE_ROOT);
    }

    private static SandboxGateway.CreateCommand command() {
        return new SandboxGateway.CreateCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "request-template",
                null);
    }
}