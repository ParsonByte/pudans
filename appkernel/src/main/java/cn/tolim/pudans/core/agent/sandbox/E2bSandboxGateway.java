package cn.tolim.pudans.core.agent.sandbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class E2bSandboxGateway extends AbstractSandboxGateway {

    private static final String PROVIDER = "e2b";

    @Autowired
    public E2bSandboxGateway(SandboxProperties properties) {
        this(properties, new E2bSandboxClient(properties));
    }

    E2bSandboxGateway(SandboxProperties properties, SandboxClient client) {
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
        return "E2B";
    }
}