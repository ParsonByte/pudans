package cn.tolim.pudans.contract.infra;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "lastTransitionTime")
public class Condition {

    @Schema(
            requiredMode = REQUIRED,
            maxLength = 316,
            pattern = "^([a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*/)?("
                    + "([A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])$")
    private String type;


    @Schema(requiredMode = REQUIRED)
    private ConditionStatus status;


    @Schema(requiredMode = REQUIRED)
    private Instant lastTransitionTime;


    @Schema(maxLength = 32768)
    @Builder.Default
    private String message = "";


    @Schema(maxLength = 1024, pattern = "^[A-Za-z]([A-Za-z0-9_,:]*[A-Za-z0-9_])?$")
    @Builder.Default
    private String reason = "";
}
