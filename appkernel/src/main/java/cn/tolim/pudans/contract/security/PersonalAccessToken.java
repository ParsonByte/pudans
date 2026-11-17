package cn.tolim.pudans.contract.security;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;


@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(
        group = "security.pudans.tolim.cn",
        version = "v1alpha1",
        kind = PersonalAccessToken.KIND,
        plural = "personalaccesstokens",
        singular = "personalaccesstoken")
public class PersonalAccessToken extends AbstractExtension {

    public static final String KIND = "PersonalAccessToken";

    public static final String PAT_TOKEN_PREFIX = "pat_";

    public static final String SCOPE_MODE_INTERSECTION = "Intersection";

    public static final String SCOPE_MODE_ROLES_ONLY = "RolesOnly";


    private Spec spec = new Spec();


    @Data
    @Schema(name = "PatSpec")
    public static class Spec {


        @Schema(requiredMode = REQUIRED)
        private String name;


        private String description;


        private Instant expiresAt;


        private List<String> roles;


        private List<String> scopes;


        private String scopeMode = SCOPE_MODE_INTERSECTION;


        @Schema(requiredMode = REQUIRED)
        private String username;


        private boolean revoked;


        @Schema(description = "Whether this token may impersonate another user with sudo.")
        private boolean sudo;


        private Instant revokesAt;


        private Instant lastUsed;


        @Schema(requiredMode = REQUIRED)
        private String tokenId;

        @Schema(readOnly = true, description = "Master key version used to sign this token.")
        private Integer keyVersion;
    }
}
