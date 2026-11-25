package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class E2bSandboxClientTest {

    private SandboxProperties properties;

    private E2bSandboxClient client;

    @BeforeEach
    void setUp() {
        properties = new SandboxProperties();
        properties.setProvider("e2b");
        properties.getE2b().setApiKey("e2b-key");
        properties.getE2b().setTemplate("base");
        properties.getE2b().setWorkspaceRoot("/home/user");
        client = new E2bSandboxClient(properties);
    }

    @Test
    void restoresThePersistedE2bState() {
        var state = "{\"sandboxId\":\"e2b-sandbox-1\","
                + "\"workspaceRoot\":\"/home/user\","
                + "\"templateId\":\"pudans-workspace\","
                + "\"sandboxDomain\":\"e2b.app\","
                + "\"envdAccessToken\":\"token\","
                + "\"persistenceMode\":\"TAR\"}";

        var session = client.resume(state);

        assertThat(session.providerSandboxId()).isEqualTo("e2b-sandbox-1");
        assertThat(session.workspaceRoot()).isEqualTo("/home/user");
        assertThat(session.providerState())
                .contains("\"sandboxId\":\"e2b-sandbox-1\"")
                .contains("\"templateId\":\"pudans-workspace\"")
                .contains("\"envdAccessToken\":\"token\"");
    }

    @Test
    void rejectsNativeSnapshotPersistence() {
        properties.getE2b().setPersistenceMode("NATIVE_SNAPSHOT");
        var session = client.create("/home/user", "base", null);

        assertThatThrownBy(session::start)
                .isInstanceOfSatisfying(SandboxGateway.Failure.class, failure -> {
                    assertThat(failure.code()).isEqualTo("sandbox-provider-not-configured");
                    assertThat(failure.getMessage()).contains("TAR");
                });
    }
}