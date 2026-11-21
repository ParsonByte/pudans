package cn.tolim.pudans.contract.user;

import static cn.tolim.pudans.contract.user.User.GROUP;
import static cn.tolim.pudans.contract.user.User.KIND;
import static cn.tolim.pudans.contract.user.User.VERSION;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.extension.api.AbstractExtension;
import cn.tolim.pudans.extension.api.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;


@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = KIND, singular = "user", plural = "users")
public class User extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "User";

    public static final String USER_RELATED_ROLES_INDEX = "roles";

    public static final String ROLE_NAMES_ANNO = "rbac.authorization.pudans.tolim.cn/role-names";

    public static final String EMAIL_TO_VERIFY = "pudans.tolim.cn/email-to-verify";

    public static final String HIDDEN_USER_LABEL = "pudans.tolim.cn/hidden-user";

    public static final String REQUEST_TO_UPDATE = "pudans.tolim.cn/request-to-update";


    @Schema(requiredMode = REQUIRED)
    private UserSpec spec = new UserSpec();


    private UserStatus status = new UserStatus();


    @Data
    public static class UserSpec {


        @Schema(readOnly = true)
        private String uid;


        @Schema(requiredMode = REQUIRED, readOnly = true)
        private String displayName;


        private String avatar;


        @Schema(readOnly = true)
        private String email;


        private boolean emailVerified;


        private String phone;


        private String password;


        private String bio;


        private Instant registeredAt;


        private Boolean twoFactorAuthEnabled;


        private String totpEncryptedSecret;


        @Schema(readOnly = true)
        private Long totpLastStep;


        private Boolean disabled;


        private Integer loginHistoryLimit;
    }


    @Data
    public static class UserStatus {


        private String permalink;


        @Schema(readOnly = true)
        private String hashId;
    }
}
