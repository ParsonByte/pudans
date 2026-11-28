package cn.tolim.pudans.iam.communal.entity.account.query;

import cn.tolim.pudans.iam.communal.enums.UserStatus;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springdoc.core.annotations.ParameterObject;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "查询用户列表入参")
@ParameterObject
public class UserListQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = -7110595216804896858L;

    // Username
    @Parameter(description = "用户名")
    private String username;

    // Full name
    @Parameter(description = "姓名")
    private String fullName;

    // Email
    @Parameter(description = "邮箱")
    private String email;

    // Phone number
    @Parameter(description = "手机号")
    private String phone;

    // Organization ID
    @Parameter(description = "组织ID")
    private String organizationId;

    // Include child organizations
    @Parameter(description = "包含子组织")
    private Boolean inclSubOrganization;

    // Status
    @Parameter(description = "状态")
    private UserStatus status;

    // Data source
    @Parameter(description = "数据来源")
    private String dataOrigin;
}
