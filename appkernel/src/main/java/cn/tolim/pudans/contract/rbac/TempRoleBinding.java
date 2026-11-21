package cn.tolim.pudans.contract.rbac;

import cn.tolim.pudans.extension.contract.AbstractExtension;
import cn.tolim.pudans.extension.contract.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = TempRoleBinding.GROUP,
        version = TempRoleBinding.VERSION,
        kind = TempRoleBinding.KIND,
        plural = "temprolebindings",
        singular = "temprolebinding")
public class TempRoleBinding extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "TempRoleBinding";

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_EXPIRED = "Expired";
    public static final String STATUS_REVOKED = "Revoked";

    public static final String SUPER_ROLE_NAME = "super-role";

    public static final long HARD_MAX_DURATION_SECONDS = 28800L;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec = new Spec();

    // 创建待审批的临时角色绑定申请。
    public static TempRoleBinding apply(
            String username, List<String> roleNames, Instant expiresAt, String reason, String ticketId,
            String requestedBy) {
        var binding = new TempRoleBinding();
        var spec = new Spec();
        spec.setUsername(username);
        spec.setRoleNames(new ArrayList<>(roleNames));
        spec.setExpiresAt(expiresAt);
        spec.setReason(reason);
        spec.setTicketId(ticketId);
        spec.setRequestedBy(requestedBy);
        spec.setStatus(STATUS_PENDING);
        binding.setSpec(spec);
        return binding;
    }

    // 仅在绑定已批准且尚未过期时生效。
    public boolean isActiveAt(Instant now) {
        return STATUS_ACTIVE.equals(spec.getStatus())
                && spec.getExpiresAt() != null
                && now.isBefore(spec.getExpiresAt());
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String username;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private List<String> roleNames = new ArrayList<>();

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private Instant expiresAt;

        private String reason;

        private String ticketId;

        private String requestedBy;

        private String approvedBy;

        private Instant approvedAt;

        private String status = STATUS_PENDING;

        private String approvalAuditId;

        private String revokeAuditId;
    }
}
