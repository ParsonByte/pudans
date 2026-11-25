package cn.tolim.pudans.core.agent.sandbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentRunSandboxGateway extends AbstractSandboxGateway {

    private static final String PROVIDER = "agentrun";

    @Autowired
    public AgentRunSandboxGateway(SandboxProperties properties) {
        this(properties, new AgentRunSandboxClient(properties));
    }

    AgentRunSandboxGateway(SandboxProperties properties, SandboxClient client) {
        super(properties, client);
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
    protected String configuredWorkspaceRoot() {
        return properties.defaultWorkspaceRoot(PROVIDER);
    }

    @Override
    protected String providerLabel() {
        return "AgentRun";
    }
}