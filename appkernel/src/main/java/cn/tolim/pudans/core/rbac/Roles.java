package cn.tolim.pudans.core.rbac;

import cn.tolim.pudans.contract.rbac.resource.Role;
import cn.tolim.pudans.contract.rbac.resource.RoleBinding;
import cn.tolim.pudans.contract.rbac.resource.RoleBinding.Subject;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface Roles {

    Flux<RoleBinding> listRoleBindings(Subject subject);

    Flux<String> getRolesByUsername(String username);

    Mono<Map<String, Collection<String>>> getRolesByUsernames(Collection<String> usernames);

    Mono<Boolean> contains(Collection<String> source, Collection<String> candidates);


    Flux<Role> listPermissions(Set<String> names);

    Flux<Role> listDependenciesFlux(Set<String> names);


    Flux<Role> list(Set<String> roleNames);


    Flux<Role> list(Set<String> roleNames, boolean excludeHidden);
}
