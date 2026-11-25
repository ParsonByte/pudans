package cn.tolim.pudans.contract.ai.dto;

public record AiSkillDescriptor(
        String key,
        String label,
        String description,
        boolean enabled) {}
