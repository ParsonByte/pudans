package cn.tolim.pudans.job.dto;

import cn.tolim.pudans.job.handler.JobHandler;

// Public description of a registered job handler
public record JobHandlerDescriptor(String name, String description, String handlerClass) {

    public static JobHandlerDescriptor from(JobHandler handler) {
        return new JobHandlerDescriptor(handler.name(), handler.description(),
                handler.getClass().getName());
    }
}