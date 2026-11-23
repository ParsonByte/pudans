package cn.tolim.pudans.extension.contract;

import io.swagger.v3.oas.annotations.media.Schema;

public record SecretTypeCount(
        @Schema(description = "Secret type.") String type,
        @Schema(description = "Secret count.") long count,
        @Schema(description = "Key entry count.") long entries) {}
