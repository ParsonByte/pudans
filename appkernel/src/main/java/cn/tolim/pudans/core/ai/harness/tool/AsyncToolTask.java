package cn.tolim.pudans.core.ai.harness.tool;

import java.util.Objects;

public record AsyncToolTask(
        String id, String toolName, AsyncToolStatus status, String output, String error) {

    public AsyncToolTask {
        id = Objects.requireNonNull(id, "任务标识不能为空");
        toolName = Objects.requireNonNull(toolName, "工具名称不能为空");
        status = Objects.requireNonNull(status, "任务状态不能为空");
        output = output == null ? "" : output;
        error = error == null ? "" : error;
    }
}