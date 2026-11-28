package cn.tolim.pudans.iam.authorization.repository;

import cn.tolim.pudans.iam.authorization.entity.AuthzRoleBindingEntity;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface AuthzRoleBindingRepository extends JpaRepository<AuthzRoleBindingEntity, String> {

    // Returns all bindings of a role
    List<AuthzRoleBindingEntity> findAllByRoleId(String roleId);

    // Returns all role bindings of a principal
    List<AuthzRoleBindingEntity> findAllByPrincipalTypeAndPrincipalId(PrincipalType principalType,
                                                                      String principalId);

    // Returns all role bindings of a principal in a scope
    List<AuthzRoleBindingEntity> findAllByPrincipalTypeAndPrincipalIdAndScope(PrincipalType principalType,
                                                                              String principalId,
                                                                              String scope);

    // Returns the binding of a role to a principal in a scope, if present
    Optional<AuthzRoleBindingEntity> findByRoleIdAndPrincipalTypeAndPrincipalIdAndScope(
            String roleId, PrincipalType principalType, String principalId, String scope);

    // Checks whether a role is bound to a principal in a scope
    boolean existsByRoleIdAndPrincipalTypeAndPrincipalIdAndScope(String roleId,
                                                                 PrincipalType principalType,
                                                                 String principalId,
                                                                 String scope);

    // Deletes all bindings of a role
    void deleteAllByRoleId(String roleId);

    // Deletes all bindings of a principal
    void deleteAllByPrincipalTypeAndPrincipalId(PrincipalType principalType, String principalId);
}