package cn.tolim.pudans.core.agent.sandbox;

import io.agentscope.extensions.sandbox.agentrun.AgentRunSandboxClient;
import io.agentscope.extensions.sandbox.agentrun.AgentRunSandboxClientOptions;
import io.agentscope.extensions.sandbox.agentrun.AgentRunSandboxState;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxState;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentRunSandboxGateway
        extends HarnessSandboxGateway<AgentRunSandboxClientOptions> {

    private static final String PROVIDER = "agentrun";

    @Autowired
    public AgentRunSandboxGateway(SandboxProperties properties) {
        this(properties, options -> new AgentRunSandboxClient(options, null));
    }

    AgentRunSandboxGateway(
            SandboxProperties properties,
            Function<AgentRunSandboxClientOptions, SandboxClient<AgentRunSandboxClientOptions>>
                    clients) {
        super(properties, clients);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean configured() {
        return properties.configured(PROVIDER);
    }

    @Override
    protected AgentRunSandboxClientOptions options(String template, String region) {
        var options = new AgentRunSandboxClientOptions();
        options.setApiKey(properties.getApiKey());
        options.setAccountId(properties.getAccountId());
        options.setRegion(StringUtils.hasText(region) ? region : properties.getRegion());
        options.setDataPlaneBaseUrl(properties.getDataPlaneBaseUrl());
        options.setTemplateName(StringUtils.hasText(template)
                ? template
                : properties.defaultTemplate(PROVIDER));
        options.setMcpServerUrl(properties.getMcpServerUrl());
        options.setWorkspaceRoot(configuredWorkspaceRoot());
        options.setSandboxIdleTimeoutSeconds(properties.sandboxIdleTimeoutSeconds());
        options.setConnectTimeoutSeconds(properties.normalizedConnectTimeoutSeconds());
        options.setReadTimeoutSeconds(properties.normalizedReadTimeoutSeconds());
        options.setMaxRetries(properties.normalizedMaxRetries());
        options.validate();
        return options;
    }

    @Override
    protected String providerSandboxId(SandboxState state) {
        return state instanceof AgentRunSandboxState agentRun ? agentRun.getSandboxId() : null;
    }

    @Override
    protected String workspaceRoot(SandboxState state) {
        return state instanceof AgentRunSandboxState agentRun
                        && StringUtils.hasText(agentRun.getWorkspaceRoot())
                ? agentRun.getWorkspaceRoot()
                : super.workspaceRoot(state);
    }

    @Override
    protected String configuredWorkspaceRoot() {
        return properties.defaultWorkspaceRoot(PROVIDER);
    }

    @Override
    protected String providerLabel() {
        return "AgentRun";
    }
}
