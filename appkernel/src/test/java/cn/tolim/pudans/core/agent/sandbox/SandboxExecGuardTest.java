package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.agent.dto.SandboxExecRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SandboxExecGuardTest {

    private static final String WORKSPACE_ROOT = "/home/pudans/workspace";

    private SandboxProperties properties;

    private SandboxExecGuard guard;

    @BeforeEach
    void setUp() {
        properties = new SandboxProperties();
        properties.setDefaultExecTimeoutSeconds(45);
        guard = new SandboxExecGuard(properties);
    }

    @Test
    void resolvesAWorkdirInsideTheWorkspaceAndAppliesTheDefaultTimeout() {
        var command = guard.validate(
                new SandboxExecRequest("  printf ok  ", "src/main", null, SandboxExecRequest.RiskLevel.LOW),
                WORKSPACE_ROOT);

        assertThat(command.command()).isEqualTo("printf ok");
        assertThat(command.workdir()).isEqualTo(WORKSPACE_ROOT + "/src/main");
        assertThat(command.timeoutSeconds()).isEqualTo(45);
    }

    @Test
    void acceptsAnAbsoluteWorkdirInsideTheWorkspace() {
        var command = guard.validate(
                new SandboxExecRequest(
                        "pwd",
                        WORKSPACE_ROOT + "/nested",
                        30,
                        SandboxExecRequest.RiskLevel.LOW),
                WORKSPACE_ROOT);

        assertThat(command.workdir()).isEqualTo(WORKSPACE_ROOT + "/nested");
    }

    @Test
    void rejectsMissingCommandsAndRiskLevels() {
        assertRejected(
                new SandboxExecRequest(" ", null, 10, SandboxExecRequest.RiskLevel.LOW),
                HttpStatus.BAD_REQUEST,
                "sandbox-command-required");
        assertRejected(
                new SandboxExecRequest("pwd", null, 10, null),
                HttpStatus.BAD_REQUEST,
                "sandbox-risk-level-required");
        assertRejected(
                null, HttpStatus.BAD_REQUEST, "sandbox-command-required");
    }

    @Test
    void rejectsMediumAndHighRiskCommandsWithoutAnApprovalChannel() {
        assertRejected(
                new SandboxExecRequest("rm -rf data", null, 10, SandboxExecRequest.RiskLevel.MEDIUM),
                HttpStatus.FORBIDDEN,
                "sandbox-approval-required");
        assertRejected(
                new SandboxExecRequest("rm -rf data", null, 10, SandboxExecRequest.RiskLevel.HIGH),
                HttpStatus.FORBIDDEN,
                "sandbox-approval-required");
    }

    @Test
    void rejectsCommandsThatExceedTheByteIndependentLengthLimit() {
        assertRejected(
                new SandboxExecRequest(
                        "x".repeat(16 * 1024 + 1),
                        null,
                        10,
                        SandboxExecRequest.RiskLevel.LOW),
                HttpStatus.BAD_REQUEST,
                "sandbox-command-too-long");
    }

    @Test
    void rejectsTimeoutsOutsideTheSupportedRange() {
        assertRejected(
                new SandboxExecRequest("pwd", null, 0, SandboxExecRequest.RiskLevel.LOW),
                HttpStatus.BAD_REQUEST,
                "sandbox-timeout-invalid");
        assertRejected(
                new SandboxExecRequest("pwd", null, 301, SandboxExecRequest.RiskLevel.LOW),
                HttpStatus.BAD_REQUEST,
                "sandbox-timeout-invalid");
    }

    @Test
    void rejectsWorkdirsOutsideTheWorkspaceAfterNormalization() {
        assertRejected(
                new SandboxExecRequest(
                        "pwd", "../other", 10, SandboxExecRequest.RiskLevel.LOW),
                HttpStatus.BAD_REQUEST,
                "sandbox-workdir-outside-workspace");
        assertRejected(
                new SandboxExecRequest(
                        "pwd", "/tmp", 10, SandboxExecRequest.RiskLevel.LOW),
                HttpStatus.BAD_REQUEST,
                "sandbox-workdir-outside-workspace");
    }

    @Test
    void rejectsAnUnavailableOrInvalidWorkspaceRoot() {
        assertRejectedWithRoot(
                new SandboxExecRequest("pwd", null, 10, SandboxExecRequest.RiskLevel.LOW),
                null,
                HttpStatus.CONFLICT,
                "sandbox-workspace-unavailable");
        assertRejectedWithRoot(
                new SandboxExecRequest("pwd", null, 10, SandboxExecRequest.RiskLevel.LOW),
                "bad\u0000root",
                HttpStatus.CONFLICT,
                "sandbox-workspace-invalid");
        assertRejectedWithRoot(
                new SandboxExecRequest("pwd", "bad\u0000dir", 10, SandboxExecRequest.RiskLevel.LOW),
                WORKSPACE_ROOT,
                HttpStatus.BAD_REQUEST,
                "sandbox-workdir-invalid");
    }

    @Test
    void resolvesRelativeAndAbsoluteFilePathsInsideTheWorkspace() {
        assertThat(guard.validateFilePath(WORKSPACE_ROOT, "notes/result.txt"))
                .isEqualTo(WORKSPACE_ROOT + "/notes/result.txt");
        assertThat(guard.validateFilePath(WORKSPACE_ROOT, WORKSPACE_ROOT + "/result.txt"))
                .isEqualTo(WORKSPACE_ROOT + "/result.txt");
    }

    @Test
    void rejectsEmptyAndEscapingFilePaths() {
        assertApiError(
                () -> guard.validateFilePath(WORKSPACE_ROOT, " "),
                HttpStatus.BAD_REQUEST,
                "sandbox-path-required");
        assertApiError(
                () -> guard.validateFilePath(WORKSPACE_ROOT, "../outside.txt"),
                HttpStatus.BAD_REQUEST,
                "sandbox-path-outside-workspace");
        assertApiError(
                () -> guard.validateFilePath(WORKSPACE_ROOT, "/tmp/outside.txt"),
                HttpStatus.BAD_REQUEST,
                "sandbox-path-outside-workspace");
    }

    @Test
    void rejectsInvalidFilePaths() {
        assertApiError(
                () -> guard.validateFilePath(WORKSPACE_ROOT, "bad\u0000file"),
                HttpStatus.BAD_REQUEST,
                "sandbox-path-invalid");
    }

    @Test
    void validatesUploadContentAgainstTheConfiguredLimit() {
        properties.setMaxFileBytes(2_048);

        assertApiError(
                () -> guard.validateUploadSize(new byte[0]),
                HttpStatus.BAD_REQUEST,
                "sandbox-file-empty");
        assertThat(guard.validateUploadSize(new byte[512])).isEqualTo(512);
        assertApiError(
                () -> guard.validateUploadSize(new byte[2_049]),
                HttpStatus.PAYLOAD_TOO_LARGE,
                "sandbox-file-too-large");
    }

    private void assertRejected(
            SandboxExecRequest request, HttpStatus status, String code) {
        assertRejectedWithRoot(request, WORKSPACE_ROOT, status, code);
    }

    private void assertRejectedWithRoot(
            SandboxExecRequest request,
            String workspaceRoot,
            HttpStatus status,
            String code) {
        assertApiError(() -> guard.validate(request, workspaceRoot), status, code);
    }

    private static void assertApiError(
            Runnable operation, HttpStatus status, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(status);
                    assertThat(error.getCode()).isEqualTo(code);
                });
    }
}
