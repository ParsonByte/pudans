package cn.tolim.pudans.mq.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record MessagePublishRequest(@NotBlank(message = "topic is required") String topic,
                                    String eventType,
                                    String producer,
                                    String bizKey,
                                    @NotNull(message = "payload is required") Object payload,
                                    Map<String, String> headers,
                                    @Min(1) @Max(100) Integer maxAttempts) {
}