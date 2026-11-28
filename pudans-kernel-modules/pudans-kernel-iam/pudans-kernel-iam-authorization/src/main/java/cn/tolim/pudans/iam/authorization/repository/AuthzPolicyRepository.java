package cn.tolim.pudans.iam.authorization.repository;

import cn.tolim.pudans.iam.authorization.entity.AuthzPolicyEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.PolicyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface AuthzPolicyRepository extends JpaRepository<AuthzPolicyEntity, String>,
        JpaSpecificationExecutor<AuthzPolicyEntity> {

    // Returns the policy with the given unique code, if present
    Optional<AuthzPolicyEntity> findByCode(String code);

    // Checks whether a policy with the given code exists
    boolean existsByCode(String code);

    // Returns all policies with the given status
    List<AuthzPolicyEntity> findAllByStatus(AuthorizationStatus status);

    // Returns all policies of the given type
    List<AuthzPolicyEntity> findAllByType(PolicyType type);

    // Returns all policies with the given status and type
    List<AuthzPolicyEntity> findAllByStatusAndType(AuthorizationStatus status, PolicyType type);
}