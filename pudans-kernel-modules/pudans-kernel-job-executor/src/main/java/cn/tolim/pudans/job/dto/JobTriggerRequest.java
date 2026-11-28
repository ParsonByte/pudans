package cn.tolim.pudans.job.dto;

import jakarta.validation.constraints.Size;

// Optional overrides for a manual job trigger
public record JobTriggerRequest(
        // Replaces the parameters stored on the definition for this execution
        String params,

        @Size(max = 100)
        String triggeredBy) {
}