package cn.tolim.pudans.contract.ai.dto;

public record AiProtocolDescriptor(
        String protocol,
        String displayName,
        String description,
        String requestPath,
        boolean runtimeSupported) {}
