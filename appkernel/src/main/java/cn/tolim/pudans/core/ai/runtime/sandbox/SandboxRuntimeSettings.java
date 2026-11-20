package cn.tolim.pudans.core.ai.runtime.sandbox;

public record SandboxRuntimeSettings(
        String logFile,
        String tencentDataPlaneTemplate,
        String tencentApiVersion,
        int requestTimeoutSeconds) {

    public SandboxRuntimeSettings {
        if (logFile == null || logFile.isBlank()) {
            throw new IllegalArgumentException("logFile 不能为空");
        }
        if (tencentDataPlaneTemplate == null || tencentDataPlaneTemplate.isBlank()) {
            throw new IllegalArgumentException("tencentDataPlaneTemplate 不能为空");
        }
        if (tencentApiVersion == null || tencentApiVersion.isBlank()) {
            throw new IllegalArgumentException("tencentApiVersion 不能为空");
        }
        if (requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 60;
        }
    }
}