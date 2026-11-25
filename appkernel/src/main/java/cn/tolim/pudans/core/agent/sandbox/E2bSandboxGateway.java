package cn.tolim.pudans.core.agent.sandbox;

import io.agentscope.extensions.sandbox.e2b.E2bPersistenceMode;
import io.agentscope.extensions.sandbox.e2b.E2bSandboxClient;
import io.agentscope.extensions.sandbox.e2b.E2bSandboxClientOptions;
import io.agentscope.extensions.sandbox.e2b.E2bSandboxState;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.agentscope.harness.agent.sandbox.SandboxState;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class E2bSandboxGateway extends HarnessSandboxGateway<E2bSandboxClientOptions> {

    private static final String PROVIDER = "e2b";

    @Autowired
    public E2bSandboxGateway(SandboxProperties properties) {
        this(properties, options -> new E2bSandboxClient(options, null));
    }

    E2bSandboxGateway(
            SandboxProperties properties,
            Function<E2bSandboxClientOptions, SandboxClient<E2bSandboxClientOptions>> clients) {
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
    protected E2bSandboxClientOptions options(String template, String region) {
        var e2b = properties.getE2b();
        var options = new E2bSandboxClientOptions();
        options.setApiKey(e2b.getApiKey().trim());
        options.setApiBaseUrl(e2b.getApiBaseUrl().trim());
        options.setDomain(e2b.getDomain().trim());
        options.setTemplateId(StringUtils.hasText(template)
                ? template
                : properties.defaultTemplate(PROVIDER));
        options.setWorkspaceRoot(configuredWorkspaceRoot());
        options.setSandboxTimeoutSeconds(properties.sandboxIdleTimeoutSeconds());
        options.setRunUser(normalized(e2b.getRunUser()));
        options.setPersistenceMode(persistenceMode(e2b.getPersistenceMode()));
        options.setConnectTimeoutSeconds(properties.normalizedConnectTimeoutSeconds());
        options.setReadTimeoutSeconds(properties.normalizedReadTimeoutSeconds());
        options.setMaxRetries(properties.normalizedMaxRetries());
        options.setSnapshotRetention(e2b.getSnapshotRetention());
        return options;
    }

    @Override
    protected String providerSandboxId(SandboxState state) {
        return state instanceof E2bSandboxState e2b ? e2b.getSandboxId() : null;
    }

    @Override
    protected String workspaceRoot(SandboxState state) {
        return state instanceof E2bSandboxState e2b && StringUtils.hasText(e2b.getWorkspaceRoot())
                ? e2b.getWorkspaceRoot()
                : super.workspaceRoot(state);
    }

    @Override
    protected String configuredWorkspaceRoot() {
        return properties.defaultWorkspaceRoot(PROVIDER);
    }

    @Override
    protected String providerLabel() {
        return "E2B";
    }

    private static E2bPersistenceMode persistenceMode(String value) {
        try {
            return E2bPersistenceMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException error) {
            throw new SandboxException.SandboxConfigurationException(
                    "E2B persistence mode must be TAR or NATIVE_SNAPSHOT", error);
        }
    }

    private static String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
