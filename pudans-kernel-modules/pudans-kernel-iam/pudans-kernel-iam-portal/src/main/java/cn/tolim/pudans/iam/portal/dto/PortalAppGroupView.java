package cn.tolim.pudans.iam.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "门户应用链接所属的应用分组")
public class PortalAppGroupView implements Serializable {

    @Serial
    private static final long serialVersionUID = 2348578143792390011L;

    // Application group ID
    @Schema(description = "应用分组ID")
    private String id;

    // Application group code
    @Schema(description = "应用分组编码")
    private String code;

    // Application group name
    @Schema(description = "应用分组名称")
    private String name;
}