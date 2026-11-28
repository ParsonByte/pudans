package cn.tolim.pudans.iam.authorization.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@Schema(description = "鉴权决策日志")
public class AuthzLogView implements Serializable {

    @Serial
    private static final long serialVersionUID = -4507609123405907114L;

    // Log ID
    @Schema(description = "日志ID")
    private String id;

    // Principal type
    @Schema(description = "主体类型")
    private String principalType;

    // Principal ID
    @Schema(description = "主体ID")
    private String principalId;

    // Application code
    @Schema(description = "应用编码")
    private String appCode;

    // Evaluated action
    @Schema(description = "动作")
    private String action;

    // Evaluated resource
    @Schema(description = "资源")
    private String resource;

    // Final decision: ALLOW or DENY
    @Schema(description = "决策")
    private String decision;

    // Decision reason
    @Schema(description = "原因")
    private String reason;

    // Decision context JSON
    @Schema(description = "上下文 JSON")
    private String context;

    // Creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}