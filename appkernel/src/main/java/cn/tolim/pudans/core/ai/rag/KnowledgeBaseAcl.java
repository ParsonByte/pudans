package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.contract.core.extension.ai.KnowledgeBase;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseAcl {

    public boolean canRead(KnowledgeBase knowledgeBase, String userId, Set<String> roleNames, String deptId) {
        if (knowledgeBase == null || knowledgeBase.getSpec() == null) {
            return false;
        }
        return knowledgeBase.getSpec().getStatus() != KnowledgeBase.BaseStatus.DISABLED
                && canView(knowledgeBase, userId, roleNames, deptId);
    }

    // 管理界面需要看到已停用的知识库，以便重新启用。
    public boolean canView(KnowledgeBase knowledgeBase, String userId, Set<String> roleNames, String deptId) {
        if (knowledgeBase == null || knowledgeBase.getSpec() == null) {
            return false;
        }
        var spec = knowledgeBase.getSpec();
        if (spec.getVisibility() == KnowledgeBase.Visibility.COMPANY) {
            return true;
        }
        if (spec.getVisibility() == KnowledgeBase.Visibility.TEAM && matches(deptId, spec.getOwnerDeptId())) {
            return true;
        }
        if (spec.getVisibility() == KnowledgeBase.Visibility.PRIVATE && matches(userId, spec.getOwnerUserId())) {
            return true;
        }
        return aclGrantsRead(spec.getAcl(), userId, roleNames, deptId);
    }

    private static boolean aclGrantsRead(
            List<KnowledgeBase.Acl> acl, String userId, Set<String> roleNames, String deptId) {
        if (acl == null || acl.isEmpty()) {
            return false;
        }
        for (var entry : acl) {
            if (entry == null
                    || entry.getSubjectType() == null
                    || entry.getSubjectId() == null
                    || entry.getSubjectId().isBlank()
                    || entry.getPermission() == null) {
                continue;
            }
            // 写权限和管理权限同样包含读取权限
            var granted = switch (entry.getSubjectType()) {
                case USER -> matches(userId, entry.getSubjectId());
                case ROLE -> roleNames != null && roleNames.contains(entry.getSubjectId());
                case DEPT -> matches(deptId, entry.getSubjectId());
            };
            if (granted) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String value, String expected) {
        return value != null && !value.isBlank() && value.equals(expected);
    }
}
