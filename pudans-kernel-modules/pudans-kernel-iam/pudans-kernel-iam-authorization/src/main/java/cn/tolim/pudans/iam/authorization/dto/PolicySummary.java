package cn.tolim.pudans.iam.authorization.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@Schema(description = "策略摘要")
public class PolicySummary implements Serializable {

    @Serial
    private static final long serialVersionUID = -7695835078651781693L;

    // Policy ID
    @Schema(description = "策略ID")
    private String id;

    // Policy name
    @Schema(description = "策略名称")
    private String name;

    // Unique policy code
    @Schema(description = "唯一策略编码")
    private String code;

    // Policy type: SYSTEM or CUSTOM
    @Schema(description = "策略类型")
    private String type;

    // Policy document version
    @Schema(description = "策略文档版本")
    private String version;

    // Policy status: ENABLED or DISABLED
    @Schema(description = "策略状态")
    private String status;

    // Policy description
    @Schema(description = "策略描述")
    private String description;

    // Creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    // Last update time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近更新时间")
    private LocalDateTime updateTime;
}