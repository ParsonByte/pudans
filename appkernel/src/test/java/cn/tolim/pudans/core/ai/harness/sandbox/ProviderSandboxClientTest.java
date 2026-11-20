package cn.tolim.pudans.core.ai.harness.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxCreateSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxHandle;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxProviderAdapter;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ProviderSandboxClientTest {

    @Mock
    SandboxProviderAdapter provider;

    @TempDir
    Path tempDir;

    @Test
    void createsSessionWithWorkspaceWorkdirAndEnvironment() {
        var workspace = new WorkspaceSpec(
                tempDir.resolve("workspace"), "skills", "/workspace", Map.of("EXTRA", "workspace"));
        var runtime = new SandboxCreateSpec(
                "agent",
                "image",
                null,
                1.0,
                512,
                120,
                false,
                "/ignored",
                Map.of("EXTRA", "runtime", "RUNTIME", "runtime"));
        when(provider.create(any())).thenReturn(Mono.just(new SandboxHandle("sandbox-1", null)));
        var client = new ProviderSandboxClient(provider);

        var session = client.create(workspace, runtime).block();

        var captor = ArgumentCaptor.forClass(SandboxCreateSpec.class);
        verify(provider).create(captor.capture());
        assertThat(captor.getValue().workdir()).isEqualTo("/workspace");
        assertThat(captor.getValue().environment())
                .containsEntry("EXTRA", "runtime")
                .containsEntry("RUNTIME", "runtime");
        assertThat(session).isNotNull();
        assertThat(session.handle().sandboxId()).isEqualTo("sandbox-1");
        assertThat(session.workspace()).isEqualTo(workspace);
    }

    @Test
    void closesSessionThroughProvider() {
        when(provider.destroy("sandbox-2")).thenReturn(Mono.empty());
        var session = new SandboxSession(
                new SandboxHandle("sandbox-2", null),
                new WorkspaceSpec(tempDir.resolve("workspace"), "skills", "/workspace", Map.of()),
                provider);

        session.close().block();

        verify(provider).destroy("sandbox-2");
    }
}