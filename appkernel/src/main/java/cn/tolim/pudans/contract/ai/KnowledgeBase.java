package cn.tolim.pudans.contract.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.extension.api.AbstractExtension;
import cn.tolim.pudans.extension.api.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
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
        group = KnowledgeBase.GROUP,
        version = KnowledgeBase.VERSION,
        kind = KnowledgeBase.KIND,
        plural = "knowledgebases",
        singular = "knowledgebase")
public class KnowledgeBase extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "KnowledgeBase";

    private Spec spec = new Spec();

    public enum Visibility {
        PRIVATE,
        TEAM,
        COMPANY
    }

    public enum ChunkStrategy {
        RECURSIVE,
        SEMANTIC,
        FIXED
    }

    public enum SubjectType {
        USER,
        ROLE,
        DEPT
    }

    public enum Permission {
        READ,
        WRITE,
        ADMIN
    }

    public enum BaseStatus {
        ENABLED,
        DISABLED
    }

    public enum ExternalProvider {
        DIFY,
        RAGFLOW,
        HAYSTACK,
        BAILIAN
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        private Visibility visibility = Visibility.TEAM;

        private String ownerUserId;

        private String ownerDeptId;

        private List<Acl> acl = new ArrayList<>();

        @Schema(requiredMode = REQUIRED)
        private String embeddingModel;

        private ChunkStrategy chunkStrategy = ChunkStrategy.RECURSIVE;

        private int chunkSize = 800;

        private int chunkOverlap = 120;

        private int topK = 5;

        private double scoreThreshold = 0.30;

        private int charBudget = 4000;

        private boolean rerankEnabled;

        private String rerankModel;

        private BaseStatus status = BaseStatus.ENABLED;

        private External external;
    }

    @Data
    public static class External {

        @Schema(requiredMode = REQUIRED)
        private ExternalProvider provider;

        @Schema(requiredMode = REQUIRED)
        private String endpoint;

        private String dataset;

        private String knowledgeBaseId;

        // 指向已加密保存凭证的 AiProviderAccount 名称，避免知识库配置中出现明文密钥
        private String credentialRef;

        private Integer topK;

        private Double scoreThreshold;

        // 百炼检索路径，支持 {knowledgeBaseId} 占位符，留空时使用默认路径
        private String retrievePath;

        private Map<String, Object> filters = new LinkedHashMap<>();
    }

    @Data
    public static class Acl {

        private SubjectType subjectType = SubjectType.USER;

        private String subjectId;

        private Permission permission = Permission.READ;
    }
}
