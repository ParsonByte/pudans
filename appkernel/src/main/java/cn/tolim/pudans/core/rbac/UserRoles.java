package cn.tolim.pudans.core.rbac;

import cn.tolim.pudans.core.rbac.group.Groups;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

// 用户的角色既可能来自直绑也可能来自用户组，统一合并后再用于登录、授权和 PAT 校验。
@Component
public class UserRoles {

    private final Roles roles;

    private final Groups groups;

    // 组合两个角色来源，保持调用方只依赖一个入口。
    public UserRoles(Roles roles, Groups groups) {
        this.roles = roles;
        this.groups = groups;
    }

    // 返回去重后的完整角色名集合。
    public Flux<String> list(String username) {
        return Flux.concat(roles.getRolesByUsername(username), groups.getRoleNamesByUsername(username))
                .distinct();
    }
}
