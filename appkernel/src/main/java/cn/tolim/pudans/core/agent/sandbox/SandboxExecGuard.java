package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.contract.agent.dto.SandboxExecRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SandboxExecGuard {

    private static final int MAX_TIMEOUT_SECONDS = 300;

    private static final int MAX_COMMAND_LENGTH = 16 * 1024;

    private final SandboxProperties properties;

    // 校验命令风险、超时和工作目录边界，拒绝未实现的审批放行路径。
    public Command validate(SandboxExecRequest request, String workspaceRoot) {
        if (request == null || !StringUtils.hasText(request.command())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "sandbox-command-required", "命令不能为空。");
        }
        var command = request.command().trim();
        if (command.length() > MAX_COMMAND_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "sandbox-command-too-long", "命令长度超过限制。");
        }
        if (request.riskLevel() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-risk-level-required",
                    "必须明确声明命令风险等级。");
        }
        if (request.riskLevel() != SandboxExecRequest.RiskLevel.LOW) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "sandbox-approval-required",
                    "中高风险命令需要审批，当前没有可用的审批执行通道。");
        }
        var timeout = request.timeoutSeconds() == null
                ? properties.normalizedDefaultExecTimeoutSeconds()
                : request.timeoutSeconds();
        if (timeout < 1 || timeout > MAX_TIMEOUT_SECONDS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-timeout-invalid",
                    "命令超时必须在 1 到 300 秒之间。");
        }
        var workdir = resolveWorkdir(workspaceRoot, request.workdir());
        return new Command(command, workdir, timeout);
    }

    public String validateFilePath(String workspaceRoot, String requested) {
        if (!StringUtils.hasText(requested)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "sandbox-path-required", "文件路径不能为空。");
        }
        return resolvePath(workspaceRoot, requested, "sandbox-path");
    }

    public int validateUploadSize(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "sandbox-file-empty", "上传文件不能为空。");
        }
        var max = properties.normalizedMaxFileBytes();
        if (content.length > max) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "sandbox-file-too-large",
                    "文件大小不能超过 " + max + " 字节。");
        }
        return content.length;
    }

    // 工作目录必须归一化后仍位于沙箱工作区。
    private static String resolveWorkdir(String workspaceRoot, String requested) {
        return resolvePath(workspaceRoot, requested, "sandbox-workdir");
    }

    private static String resolvePath(String workspaceRoot, String requested, String code) {
        if (!StringUtils.hasText(workspaceRoot)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "sandbox-workspace-unavailable",
                    "沙箱工作区尚未就绪。");
        }
        Path root;
        try {
            root = Path.of(workspaceRoot).normalize();
        } catch (InvalidPathException error) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "sandbox-workspace-invalid",
                    "沙箱工作区路径无效。");
        }
        if (!StringUtils.hasText(requested)) {
            return root.toString();
        }
        try {
            var resolved = Path.of(requested);
            var target = resolved.isAbsolute() ? resolved.normalize() : root.resolve(resolved).normalize();
            if (!target.startsWith(root)) {
                throw outsideWorkspace(code);
            }
            return target.toString();
        } catch (InvalidPathException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, code + "-invalid", "工作目录路径无效。");
        }
    }

    private static ApiException outsideWorkspace(String code) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                code + "-outside-workspace",
                "路径必须位于沙箱工作区内。");
    }

    public record Command(String command, String workdir, int timeoutSeconds) {}
}
