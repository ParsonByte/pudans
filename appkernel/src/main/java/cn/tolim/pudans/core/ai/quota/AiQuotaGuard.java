package cn.tolim.pudans.core.ai.quota;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import java.util.Collection;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiQuotaGuard {

    public static final String MANAGE_ROLE = "role-template-manage-ai-quota";

    public void requireInspection(Collection<String> roles) {
        if (roles != null
                && (roles.contains(MANAGE_ROLE) || AuthorityUtils.containsSuperRole(roles))) {
            return;
        }
        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "ai-quota-inspection-forbidden",
                "只有配额管理角色可以查询其他主体的配额。");
    }
}
