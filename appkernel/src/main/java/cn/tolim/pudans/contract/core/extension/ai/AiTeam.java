package cn.tolim.pudans.contract.core.extension.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
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
        group = AiTeam.GROUP,
        version = AiTeam.VERSION,
        kind = AiTeam.KIND,
        plural = "aiteams",
        singular = "aiteam")
public class AiTeam extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiTeam";

    private Spec spec = new Spec();

    public enum TeamStatus {
        ACTIVE,
        ARCHIVED
    }

    public enum TeamRole {
        ADMIN,
        MEMBER,
        REVIEWER
    }

    public enum MemberStatus {
        ACTIVE,
        REMOVED
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        @Schema(requiredMode = REQUIRED)
        private String ownerUserId;

        private TeamStatus status = TeamStatus.ACTIVE;

        private String defaultAgent;

        private List<Member> members = new ArrayList<>();

        private Map<String, Object> metadata = new LinkedHashMap<>();
    }

    @Data
    public static class Member {

        @Schema(requiredMode = REQUIRED)
        private String userId;

        private TeamRole role = TeamRole.MEMBER;

        private MemberStatus status = MemberStatus.ACTIVE;
    }
}
