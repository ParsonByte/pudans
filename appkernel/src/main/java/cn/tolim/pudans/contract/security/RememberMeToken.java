package cn.tolim.pudans.contract.security;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;


@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
        group = "security.pudans.tolim.cn",
        version = "v1alpha1",
        kind = "RememberMeToken",
        plural = "remembermetokens",
        singular = "remembermetoken")
public class RememberMeToken extends AbstractExtension {


    @Schema(requiredMode = REQUIRED)
    private Spec spec;


    @Data
    @Accessors(chain = true)
    @Schema(name = "RememberMeTokenSpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String username;


        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String series;


        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String tokenValue;


        @Nullable
        private Instant lastUsed;


        @Nullable
        private String previousTokenValue;
    }
}
