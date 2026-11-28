package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "匹配的策略语句")
public class MatchedStatementView implements Serializable {

    @Serial
    private static final long serialVersionUID = 2803843679762480546L;

    // Policy code that owns the statement
    @Schema(description = "策略编码")
    private String policyCode;

    // Statement identifier
    @Schema(description = "语句标识")
    private String sid;

    // Statement effect: Allow or Deny
    @Schema(description = "语句效果")
    private String effect;
}