package cn.tolim.pudans.iam.communal.entity.identitysource.config;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "策略配置")
public class StrategyConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = -1L;

    // Organization policy
    @Valid
    @NotNull(message = "组织策略不能为空")
    @Parameter(description = "组织策略")
    private Organization organization;

    // User policy
    @Valid
    @NotNull(message = "用户策略不能为空")
    @Parameter(description = "用户策略")
    private User user;

    @Data
    public static class Organization implements Serializable {

        @Serial
        private static final long serialVersionUID = -1L;

        // Target ID
        @NotEmpty(message = "目标ID不能为空")
        @Parameter(description = "目标ID")
        private String targetId;
    }

    @Data
    public static class User implements Serializable {

        @Serial
        private static final long serialVersionUID = -1L;

        // Default password
        @Parameter(description = "默认密码")
        private String defaultPassword;

        // Enabled
        @Parameter(description = "是否启用")
        private Boolean enabled;

        // Email notification
        @Parameter(description = "邮件通知")
        private Boolean emailNotify;
    }
}
