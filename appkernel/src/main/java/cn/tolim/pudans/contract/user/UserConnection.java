package cn.tolim.pudans.contract.user;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.extension.contract.AbstractExtension;
import cn.tolim.pudans.extension.contract.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
        group = "pudans.tolim.cn",
        version = "v1alpha1",
        kind = "UserConnection",
        singular = "userconnection",
        plural = "userconnections")
public class UserConnection extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "UserConnection";


    @Schema(requiredMode = REQUIRED)
    private UserConnectionSpec spec;


    @Data
    public static class UserConnectionSpec {


        @Schema(requiredMode = REQUIRED)
        private String registrationId;


        @Schema(requiredMode = REQUIRED)
        private String username;


        @Schema(requiredMode = REQUIRED)
        private String providerUserId;


        private String displayName;


        @Schema(readOnly = true)
        private String publicKeyCose;


        @Schema(readOnly = true)
        private Long signCount;


        @Schema(readOnly = true)
        private String aaguid;


        private java.util.List<String> transports;


        private Instant updatedAt;
    }
}
