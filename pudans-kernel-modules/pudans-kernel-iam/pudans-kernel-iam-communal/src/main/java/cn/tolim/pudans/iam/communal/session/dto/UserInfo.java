package cn.tolim.pudans.iam.communal.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Data
@Schema(description = "已认证用户信息与声明")
public class UserInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 7533866477573195194L;

    // User ID (subject)
    @Schema(description = "用户ID")
    private String userId;

    // Username
    @Schema(description = "用户名")
    private String username;

    // User type: USER or ADMIN
    @Schema(description = "用户类型")
    private String userType;

    // Organization ID
    @Schema(description = "组织ID")
    private String orgId;

    // Full name
    @Schema(description = "全名")
    private String fullName;

    // Nickname
    @Schema(description = "昵称")
    private String nickName;

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

    // Role codes granted to the user
    @Schema(description = "角色编码列表")
    private List<String> roles;

    // User group codes the user belongs to
    @Schema(description = "用户组编码列表")
    private List<String> groups;

    // Additional claims
    @Schema(description = "附加声明")
    private Map<String, Object> attributes;
}