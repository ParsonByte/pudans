package cn.tolim.pudans.iam.communal.entity.account.query;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springdoc.core.annotations.ParameterObject;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "查询用户组成员列表入参")
@ParameterObject
public class UserGroupMemberListQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = -7110595216804896858L;
    // Group ID
    @NotEmpty(message = "组ID不能为空")
    @Parameter(description = "组ID")
    private String id;
    // Username
    @Parameter(description = "用户姓名")
    private String fullName;
}
