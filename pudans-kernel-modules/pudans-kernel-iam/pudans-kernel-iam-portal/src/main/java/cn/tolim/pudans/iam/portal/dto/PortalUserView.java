package cn.tolim.pudans.iam.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "当前门户用户信息")
public class PortalUserView implements Serializable {

    @Serial
    private static final long serialVersionUID = 8485231987507351917L;

    // User ID
    @Schema(description = "用户ID")
    private String userId;

    // Username
    @Schema(description = "用户名")
    private String username;

    // Display name
    @Schema(description = "显示名称")
    private String displayName;

    // User type: USER or ADMIN
    @Schema(description = "用户类型")
    private String userType;

    // Primary organization ID
    @Schema(description = "主组织ID")
    private String organizationId;

    // Primary organization name
    @Schema(description = "主组织名称")
    private String organizationName;

    // Email address
    @Schema(description = "邮箱地址")
    private String email;

    // Phone number
    @Schema(description = "手机号")
    private String phone;

    // Avatar URL
    @Schema(description = "头像地址")
    private String avatar;

    // Whether the email address is verified
    @Schema(description = "邮箱是否已验证")
    private Boolean emailVerified;

    // Whether the phone number is verified
    @Schema(description = "手机号是否已验证")
    private Boolean phoneVerified;

    // User group codes
    @Schema(description = "用户组编码列表")
    private List<String> groups = new ArrayList<>();
}