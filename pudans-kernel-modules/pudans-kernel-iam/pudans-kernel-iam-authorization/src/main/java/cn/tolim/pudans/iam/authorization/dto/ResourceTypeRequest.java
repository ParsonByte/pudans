package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "资源类型声明请求")
public class ResourceTypeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5032590588771262255L;

    // Application code that owns the resource type
    @NotBlank(message = "appCode must not be blank")
    @Schema(description = "应用编码")
    private String appCode;

    // Resource type code, the application prefix is added when omitted
    @NotBlank(message = "code must not be blank")
    @Schema(description = "资源类型编码，格式为 {appCode}:{resourceType}")
    private String code;

    // Resource type name
    @NotBlank(message = "name must not be blank")
    @Schema(description = "资源类型名称")
    private String name;

    // Resource pattern, for example org/{orgId}/{appCode}/{resourceType}/{resourceId}
    @NotBlank(message = "pattern must not be blank")
    @Schema(description = "资源模式")
    private String pattern;

    // Status: ENABLED or DISABLED, defaults to ENABLED
    @Schema(description = "状态")
    private String status;
}