package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.PolicyAttachmentRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicyAttachmentView;
import cn.tolim.pudans.iam.authorization.entity.AuthzPolicyAttachmentEntity;
import cn.tolim.pudans.iam.authorization.entity.AuthzPolicyEntity;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class PolicyAttachmentService {

    private final AuthzPolicyAttachmentRepository attachmentRepository;
    private final PolicyService policyService;
    private final PrincipalValidator principalValidator;
    private final AuthzDecisionCache decisionCache;

    // Attaches a policy to a principal
    // Parameter request: PolicyAttachmentRequest
    // Returns: PolicyAttachmentView
    @Transactional(rollbackFor = Exception.class)
    public PolicyAttachmentView attach(PolicyAttachmentRequest request) {
        AuthzPolicyEntity policy = policyService.requirePolicy(request.getPolicyId());
        PrincipalType principalType = principalValidator.parseType(request.getPrincipalType());
        principalValidator.validate(principalType, request.getPrincipalId());
        String scope = normalizeScope(request.getScope());
        if (attachmentRepository.existsByPolicyIdAndPrincipalTypeAndPrincipalIdAndScope(
                policy.getId(), principalType, request.getPrincipalId(), scope)) {
            throw AuthorizationErrors.conflict("Policy '" + policy.getCode()
                    + "' is already attached to principal '" + request.getPrincipalId()
                    + "' in scope '" + scope + "'");
        }
        AuthzPolicyAttachmentEntity entity = new AuthzPolicyAttachmentEntity()
                .setPolicyId(policy.getId())
                .setPrincipalType(principalType)
                .setPrincipalId(request.getPrincipalId())
                .setScope(scope);
        EntityAuditSupport.stampCreate(entity);
        AuthzPolicyAttachmentEntity saved = attachmentRepository.save(entity);
        decisionCache.invalidateAll();
        return toView(saved, policy.getCode());
    }

    // Removes a policy attachment
    // Parameter id: String attachment ID
    @Transactional(rollbackFor = Exception.class)
    public void detach(String id) {
        AuthzPolicyAttachmentEntity entity = requireAttachment(id);
        attachmentRepository.delete(entity);
        decisionCache.invalidateAll();
    }

    // Lists attachments of a policy
    // Parameter policyId: String policy ID
    // Returns: List<PolicyAttachmentView>
    public List<PolicyAttachmentView> listByPolicy(String policyId) {
        AuthzPolicyEntity policy = policyService.requirePolicy(policyId);
        return attachmentRepository.findAllByPolicyId(policyId).stream()
                .map(attachment -> toView(attachment, policy.getCode())).toList();
    }

    // Lists attachments of a principal
    // Parameter principalType: PrincipalType
    // Parameter principalId: String principal ID
    // Returns: List<PolicyAttachmentView>
    public List<PolicyAttachmentView> listByPrincipal(PrincipalType principalType, String principalId) {
        List<AuthzPolicyAttachmentEntity> attachments = attachmentRepository
                .findAllByPrincipalTypeAndPrincipalId(principalType, principalId);
        List<PolicyAttachmentView> views = new ArrayList<>();
        for (AuthzPolicyAttachmentEntity attachment : attachments) {
            String policyCode = policyService.requirePolicy(attachment.getPolicyId()).getCode();
            views.add(toView(attachment, policyCode));
        }
        return views;
    }

    private AuthzPolicyAttachmentEntity requireAttachment(String id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> AuthorizationErrors.notFound("Policy attachment '" + id
                        + "' does not exist"));
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return AuthorizationConstants.SCOPE_ALL;
        }
        return scope.trim();
    }

    private static PolicyAttachmentView toView(AuthzPolicyAttachmentEntity entity, String policyCode) {
        PolicyAttachmentView view = new PolicyAttachmentView();
        view.setId(entity.getId());
        view.setPolicyId(entity.getPolicyId());
        view.setPolicyCode(policyCode);
        view.setPrincipalType(entity.getPrincipalType() == null ? null
                : entity.getPrincipalType().getCode());
        view.setPrincipalId(entity.getPrincipalId());
        view.setScope(entity.getScope());
        return view;
    }
}