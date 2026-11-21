package cn.tolim.pudans.contract.ai.resource;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = AiSandboxProvider.GROUP,
        version = AiSandboxProvider.VERSION,
        kind = AiSandboxProvider.KIND,
        plural = "aisandboxproviders",
        singular = "aisandboxprovider")
public class AiSandboxProvider extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiSandboxProvider";

    private Spec spec = new Spec();

    public enum Provider {
        ALIYUN,
        TENCENT,
        E2B
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private Provider provider;

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String endpoint;

        private String region;

        private String accessKeyId;

        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        private String accessKeySecret;

        private String defaultImage;

        private Integer maxTimeoutSeconds = 3600;

        private boolean enabled = true;
    }
}