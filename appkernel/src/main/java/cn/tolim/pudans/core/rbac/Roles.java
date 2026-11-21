package cn.tolim.pudans.core.rbac;

import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.RoleBinding;
import cn.tolim.pudans.contract.rbac.RoleBinding.Subject;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface Roles {

    // 列出主体名下的未删除角色绑定。
    Flux<RoleBinding> listRoleBindings(Subject subject);

    // 返回用户直接绑定的角色名。
    Flux<String> getRolesByUsername(String username);

    // 批量返回用户与角色名的映射。
    Mono<Map<String, Collection<String>>> getRolesByUsernames(Collection<String> usernames);

    // 判断来源角色的依赖闭包是否覆盖全部候选。
    Mono<Boolean> contains(Collection<String> source, Collection<String> candidates);


    // 列出用于界面权限汇总的角色，超管可见全部。
    Flux<Role> listPermissions(Set<String> names);

    // 列出角色及其依赖闭包，查询结果走缓存。
    Flux<Role> listDependenciesFlux(Set<String> names);


    // 按名称列出未删除的角色。
    Flux<Role> list(Set<String> roleNames);


    // 按名称列出角色，可选择排除隐藏角色。
    Flux<Role> list(Set<String> roleNames, boolean excludeHidden);
}
