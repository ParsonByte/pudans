package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentRunSandboxClientTest {

    private SandboxProperties properties;

    private AgentRunSandboxClient client;

    @BeforeEach
    void setUp() {
        properties = new SandboxProperties();
        properties.setApiKey("api-key");
        properties.setAccountId("account-1");
        properties.setRegion("cn-shanghai");
        properties.setTemplate("default-template");
        properties.setMcpServerUrl("https://mcp.example.com");
        properties.setWorkspaceRoot("/home/pudans/workspace");
        client = new AgentRunSandboxClient(properties);
    }

    @Test
    void restoresThePersistedAgentRunState() {
        var state = "{\"sandboxId\":\"01JSANDBOX0000000000000000\","
                + "\"workspaceRoot\":\"/home/pudans/workspace\","
                + "\"templateName\":\"working-template\","
                + "\"accountId\":\"account-1\","
                + "\"region\":\"cn-beijing\","
                + "\"mcpServerUrl\":\"https://mcp.example.com\","
                + "\"sandboxOwned\":true}";

        var session = client.resume(state);

        assertThat(session.providerSandboxId()).isEqualTo("01JSANDBOX0000000000000000");
        assertThat(session.workspaceRoot()).isEqualTo("/home/pudans/workspace");
        assertThat(session.providerState())
                .contains("\"sandboxId\":\"01JSANDBOX0000000000000000\"")
                .contains("\"templateName\":\"working-template\"")
                .contains("\"region\":\"cn-beijing\"");
    }

    @Test
    void rejectsAResumeWithoutProviderState() {
        assertThatThrownBy(() -> client.resume(""))
                .isInstanceOfSatisfying(SandboxGateway.Failure.class, failure ->
                        assertThat(failure.code()).isEqualTo("sandbox-state-missing"));
    }
}