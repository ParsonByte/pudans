package cn.tolim.pudans.iam.authorization.repository;

import cn.tolim.pudans.iam.authorization.entity.AuthzPolicyAttachmentEntity;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface AuthzPolicyAttachmentRepository extends JpaRepository<AuthzPolicyAttachmentEntity, String> {

    // Returns all attachments of a policy
    List<AuthzPolicyAttachmentEntity> findAllByPolicyId(String policyId);

    // Returns all policy attachments of a principal
    List<AuthzPolicyAttachmentEntity> findAllByPrincipalTypeAndPrincipalId(PrincipalType principalType,
                                                                           String principalId);

    // Returns all policy attachments of a principal in a scope
    List<AuthzPolicyAttachmentEntity> findAllByPrincipalTypeAndPrincipalIdAndScope(PrincipalType principalType,
                                                                                   String principalId,
                                                                                   String scope);

    // Returns the attachment of a policy to a principal in a scope, if present
    Optional<AuthzPolicyAttachmentEntity> findByPolicyIdAndPrincipalTypeAndPrincipalIdAndScope(
            String policyId, PrincipalType principalType, String principalId, String scope);

    // Checks whether a policy is attached to a principal in a scope
    boolean existsByPolicyIdAndPrincipalTypeAndPrincipalIdAndScope(String policyId,
                                                                   PrincipalType principalType,
                                                                   String principalId,
                                                                   String scope);

    // Deletes all attachments of a policy
    void deleteAllByPolicyId(String policyId);

    // Deletes all attachments of a principal
    void deleteAllByPrincipalTypeAndPrincipalId(PrincipalType principalType, String principalId);
}