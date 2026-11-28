package cn.tolim.pudans.job.dto;

import cn.tolim.pudans.job.enums.BlockStrategy;
import cn.tolim.pudans.job.enums.ScheduleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Create or replace a job definition
public record JobDefinitionRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9_.-]+",
                message = "code may only contain letters, digits, dot, underscore and dash")
        String code,

        @NotBlank @Size(max = 200)
        String name,

        @NotBlank @Size(max = 200)
        String handlerName,

        @NotNull
        ScheduleType scheduleType,

        @Size(max = 200)
        String cronExpression,

        // Period in milliseconds for FIXED_RATE and FIXED_DELAY schedules
        @Positive
        Long periodMillis,

        // Delay before the first fixed-rate or fixed-delay execution
        @PositiveOrZero
        Long initialDelayMillis,

        String params,

        // Zero disables the timeout
        @PositiveOrZero
        Integer timeoutSeconds,

        @Min(0) @Max(100)
        Integer maxRetries,

        @PositiveOrZero
        Integer retryIntervalSeconds,

        BlockStrategy blockStrategy,

        Boolean enabled,

        @Size(max = 500)
        String description,

        String remark) {
}