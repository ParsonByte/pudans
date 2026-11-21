package cn.tolim.pudans.contract.storage;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.extension.api.AbstractExtension;
import cn.tolim.pudans.extension.api.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = StorageFile.GROUP,
        version = StorageFile.VERSION,
        kind = StorageFile.KIND,
        plural = "storagefiles",
        singular = "storagefile")
public class StorageFile extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "StorageFile";

    private Spec spec = new Spec();

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String policy;

        @Schema(requiredMode = REQUIRED)
        private String storageKey;

        @Schema(requiredMode = REQUIRED)
        private String originalName;

        private String extension;

        private String contentType;

        private long size;

        private String owner;

        private String module;

        private Map<String, String> attributes = new LinkedHashMap<>();

        private StorageFileState state = StorageFileState.UPLOADING;

        private String sha256;

        private long chunkSize;

        private int partCount;

        private String nativeUploadId;

        private Instant uploadExpiresAt;

        private Instant uploadedAt;

        private Instant trashedAt;

        private Instant trashExpiresAt;
    }
}
