package cn.tolim.pudans.iam.authorization.repository;

import cn.tolim.pudans.iam.authorization.entity.AuthzRoleEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface AuthzRoleRepository extends JpaRepository<AuthzRoleEntity, String>,
        JpaSpecificationExecutor<AuthzRoleEntity> {

    // Returns the role with the given unique code, if present
    Optional<AuthzRoleEntity> findByCode(String code);

    // Checks whether a role with the given code exists
    boolean existsByCode(String code);

    // Returns all roles with the given status
    List<AuthzRoleEntity> findAllByStatus(AuthorizationStatus status);

    // Returns all roles with the given codes
    List<AuthzRoleEntity> findAllByCodeIn(List<String> codes);
}