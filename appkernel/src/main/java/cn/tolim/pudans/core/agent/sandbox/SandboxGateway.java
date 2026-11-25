package cn.tolim.pudans.core.agent.sandbox;

import java.io.InputStream;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface SandboxGateway {

    String provider();

    boolean configured();

    Mono<Created> create(CreateCommand command);

    Mono<Execution> exec(ExecCommand command);

    Mono<InputStream> persistWorkspace(UUID sandboxId, String providerState);

    Mono<Resumed> resume(ResumeCommand command);

    Mono<Void> upload(UploadCommand command);

    Mono<Downloaded> download(DownloadCommand command);

    Mono<Void> stop(UUID sandboxId, String providerState);

    Mono<Void> destroy(UUID sandboxId, String providerState);

    record CreateCommand(
            UUID sandboxId,
            UUID agentSessionId,
            String template,
            String region) {}

    record Created(
            String providerSandboxId,
            String providerState,
            String workspaceRoot) {}

    record ExecCommand(
            UUID sandboxId,
            String providerState,
            String command,
            String workdir,
            int timeoutSeconds) {}

    record Execution(
            int exitCode,
            String stdout,
            String stderr,
            boolean truncated) {}

    record ResumeCommand(
            UUID sandboxId,
            String providerState,
            String template,
            String region) {}

    record Resumed(
            String providerSandboxId,
            String providerState,
            String workspaceRoot) {}

    record UploadCommand(
            UUID sandboxId,
            String providerState,
            String template,
            String region,
            String path,
            byte[] content,
            String sha256) {}

    record DownloadCommand(
            UUID sandboxId,
            String providerState,
            String template,
            String region,
            String path) {}

    record Downloaded(byte[] content, String sha256) {}

    class Failure extends RuntimeException {

        private final String code;

        public Failure(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    class ExecTimeout extends RuntimeException {

        public ExecTimeout(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
