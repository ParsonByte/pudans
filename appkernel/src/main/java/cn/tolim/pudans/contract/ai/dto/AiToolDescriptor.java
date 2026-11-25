package cn.tolim.pudans.contract.ai.dto;

public record AiToolDescriptor(
        String key,
        String label,
        String description,
        boolean enabled) {}
