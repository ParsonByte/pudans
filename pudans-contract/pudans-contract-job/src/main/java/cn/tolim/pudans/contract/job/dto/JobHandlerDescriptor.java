package cn.tolim.pudans.contract.job.dto;

// Public description of a registered job handler
public record JobHandlerDescriptor(String name, String description, String handlerClass) {
}