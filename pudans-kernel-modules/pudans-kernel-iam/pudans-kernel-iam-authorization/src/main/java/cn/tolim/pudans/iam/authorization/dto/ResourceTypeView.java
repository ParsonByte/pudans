package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "资源类型视图")
public class ResourceTypeView implements Serializable {

    @Serial
    private static final long serialVersionUID = 5335021359766082520L;

    // Resource type ID
    @Schema(description = "资源类型ID")
    private String id;

    // Application ID
    @Schema(description = "应用ID")
    private String appId;

    // Application code
    @Schema(description = "应用编码")
    private String appCode;

    // Resource type code
    @Schema(description = "资源类型编码")
    private String code;

    // Resource type name
    @Schema(description = "资源类型名称")
    private String name;

    // Resource pattern
    @Schema(description = "资源模式")
    private String pattern;

    // Status
    @Schema(description = "状态")
    private String status;
}