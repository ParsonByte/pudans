package cn.tolim.pudans.contract.storage;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = StoragePolicy.GROUP,
        version = StoragePolicy.VERSION,
        kind = StoragePolicy.KIND,
        plural = "storagepolicies",
        singular = "storagepolicy")
public class StoragePolicy extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "StoragePolicy";

    private Spec spec = new Spec();

    private Status status = new Status();

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        @Schema(requiredMode = REQUIRED)
        private StoragePolicyType type;

        private String server;

        private String bucketName;

        private boolean privateBucket;

        private long maxSize;

        @Schema(requiredMode = REQUIRED)
        private String dirNameRule = "uploads/{path}";

        @Schema(requiredMode = REQUIRED)
        private String fileNameRule = "{randomkey8}_{originname}";

        private long chunkSize;

        private List<String> fileTypes = new ArrayList<>();

        private boolean fileTypeDenyList;

        private String nameRegexp;

        private boolean nameRegexpDenyList;

        private boolean proxyDownload = true;

        private boolean defaultPolicy;

        private Credentials credentials = new Credentials();

        private Settings settings = new Settings();
    }

    @Data
    public static class Credentials {

        private String accessKey;

        private String secretKey;

        private String token;

        private String clientSecret;
    }

    @Data
    public static class Settings {

        private String region;

        private boolean s3PathStyle;

        private int deleteBatchSize = 1000;

        private String tenantId;

        private String driveId;
    }

    @Data
    public static class Status {

        private boolean ready;

        private String message;

        private Instant lastTestedAt;

        private Map<String, Boolean> credentialConfigured = new LinkedHashMap<>();
    }
}
