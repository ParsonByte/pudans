package cn.tolim.pudans.contract.core.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = "pudans.tolim.cn",
        version = "v1alpha1",
        kind = "AuthProvider",
        singular = "authprovider",
        plural = "authproviders")
public class AuthProvider extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AuthProvider";

    public static final String AUTH_BINDING_LABEL = "auth.pudans.tolim.cn/auth-binding";

    public static final String PRIVILEGED_LABEL = "auth.pudans.tolim.cn/privileged";


    @Schema(requiredMode = REQUIRED)
    private AuthProviderSpec spec;


    public enum AuthType {
        FORM,
        OAUTH2
    }

    @Data
    @ToString
    public static class AuthProviderSpec {


        @Schema(requiredMode = REQUIRED)
        private String displayName;


        private String description;


        private String logo;


        private String website;


        private String helpPage;


        @Schema(requiredMode = REQUIRED)
        private String authenticationUrl;


        private String method = "GET";


        private boolean rememberMeSupport = false;


        @Schema(requiredMode = REQUIRED)
        private AuthType authType = AuthType.OAUTH2;


        private String bindingUrl;


        private String unbindUrl;


        @Schema(requiredMode = NOT_REQUIRED)
        private SettingRef settingRef;


        @Schema(requiredMode = NOT_REQUIRED)
        private ConfigMapRef configMapRef;

        public void setAuthType(@Nullable AuthType authType) {
            this.authType = (authType == null ? AuthType.OAUTH2 : authType);
        }
    }

    @Data
    @ToString
    public static class SettingRef {

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String name;


        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String group;
    }

    @Data
    @ToString
    public static class ConfigMapRef {

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String name;
    }
}
