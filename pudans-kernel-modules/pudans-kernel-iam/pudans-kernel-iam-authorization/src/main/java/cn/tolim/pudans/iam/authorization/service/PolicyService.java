package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.PolicyDetail;
import cn.tolim.pudans.iam.authorization.dto.PolicyDocument;
import cn.tolim.pudans.iam.authorization.dto.PolicyRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicySummary;
import cn.tolim.pudans.iam.authorization.entity.AuthzPolicyEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.PolicyType;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyAttachmentRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class PolicyService {

    private final AuthzPolicyRepository policyRepository;
    private final AuthzPolicyAttachmentRepository attachmentRepository;
    private final PolicyDocumentService policyDocumentService;
    private final AuthzDecisionCache decisionCache;

    // Creates a policy
    // Parameter request: PolicyRequest
    // Returns: PolicyDetail
    @Transactional(rollbackFor = Exception.class)
    public PolicyDetail create(PolicyRequest request) {
        String code = request.getCode().trim();
        if (policyRepository.existsByCode(code)) {
            throw AuthorizationErrors.conflict("Policy code '" + code + "' already exists");
        }
        policyDocumentService.validateOrThrow(request.getDocument());
        policyDocumentService.validateActionsRegistered(request.getDocument());
        AuthzPolicyEntity entity = new AuthzPolicyEntity();
        apply(entity, request, code);
        EntityAuditSupport.stampCreate(entity);
        AuthzPolicyEntity saved = policyRepository.save(entity);
        decisionCache.invalidateAll();
        return toDetail(saved);
    }

    // Updates a policy
    // Parameter id: String policy ID
    // Parameter request: PolicyRequest
    // Returns: PolicyDetail
    @Transactional(rollbackFor = Exception.class)
    public PolicyDetail update(String id, PolicyRequest request) {
        AuthzPolicyEntity entity = requirePolicy(id);
        String code = request.getCode().trim();
        if (!code.equals(entity.getCode()) && policyRepository.existsByCode(code)) {
            throw AuthorizationErrors.conflict("Policy code '" + code + "' already exists");
        }
        policyDocumentService.validateOrThrow(request.getDocument());
        policyDocumentService.validateActionsRegistered(request.getDocument());
        apply(entity, request, code);
        EntityAuditSupport.stampUpdate(entity);
        AuthzPolicyEntity saved = policyRepository.save(entity);
        decisionCache.invalidateAll();
        return toDetail(saved);
    }

    // Deletes a policy and its attachments
    // Parameter id: String policy ID
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AuthzPolicyEntity entity = requirePolicy(id);
        attachmentRepository.deleteAllByPolicyId(id);
        policyRepository.delete(entity);
        decisionCache.invalidateAll();
    }

    // Returns a policy detail
    // Parameter id: String policy ID
    // Returns: PolicyDetail
    public PolicyDetail get(String id) {
        return toDetail(requirePolicy(id));
    }

    // Lists policies filtered by type, status and keyword
    // Parameter type: PolicyType optional type filter
    // Parameter status: AuthorizationStatus optional status filter
    // Parameter keyword: String optional name or code keyword
    // Returns: List<PolicySummary>
    public List<PolicySummary> list(PolicyType type, AuthorizationStatus status, String keyword) {
        Specification<AuthzPolicyEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("code")), pattern)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return policyRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "createTime"))
                .stream().map(this::toSummary).toList();
    }

    // Returns the policy with the given ID or throws when it does not exist
    // Parameter id: String policy ID
    // Returns: AuthzPolicyEntity
    public AuthzPolicyEntity requirePolicy(String id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> AuthorizationErrors.notFound("Policy '" + id + "' does not exist"));
    }

    // Maps a policy entity to a summary
    // Parameter entity: AuthzPolicyEntity
    // Returns: PolicySummary
    public PolicySummary toSummary(AuthzPolicyEntity entity) {
        PolicySummary summary = new PolicySummary();
        summary.setId(entity.getId());
        summary.setName(entity.getName());
        summary.setCode(entity.getCode());
        summary.setType(entity.getType() == null ? null : entity.getType().getCode());
        summary.setVersion(entity.getVersion());
        summary.setStatus(entity.getStatus() == null ? null : entity.getStatus().getCode());
        summary.setDescription(entity.getDescription());
        summary.setCreateTime(entity.getCreateTime());
        summary.setUpdateTime(entity.getUpdateTime());
        return summary;
    }

    // Maps a policy entity to a detail, including its parsed document
    // Parameter entity: AuthzPolicyEntity
    // Returns: PolicyDetail
    public PolicyDetail toDetail(AuthzPolicyEntity entity) {
        PolicyDetail detail = new PolicyDetail();
        detail.setId(entity.getId());
        detail.setName(entity.getName());
        detail.setCode(entity.getCode());
        detail.setType(entity.getType() == null ? null : entity.getType().getCode());
        detail.setVersion(entity.getVersion());
        detail.setStatus(entity.getStatus() == null ? null : entity.getStatus().getCode());
        detail.setDescription(entity.getDescription());
        detail.setCreateTime(entity.getCreateTime());
        detail.setUpdateTime(entity.getUpdateTime());
        detail.setDocument(policyDocumentService.parseOrNull(entity.getDocument()));
        detail.setAttachmentCount((long) attachmentRepository.findAllByPolicyId(entity.getId()).size());
        return detail;
    }

    private void apply(AuthzPolicyEntity entity, PolicyRequest request, String code) {
        entity.setName(request.getName().trim());
        entity.setCode(code);
        entity.setType(parseType(request.getType()));
        entity.setVersion(resolveVersion(request));
        entity.setDocument(policyDocumentService.serialize(request.getDocument()));
        entity.setStatus(parseStatus(request.getStatus()));
        entity.setDescription(request.getDescription());
    }

    private static PolicyType parseType(String code) {
        if (code == null || code.isBlank()) {
            return PolicyType.CUSTOM;
        }
        PolicyType type = PolicyType.getType(code.trim());
        if (type == null) {
            throw AuthorizationErrors.badRequest("Unsupported policy type '" + code + "'");
        }
        return type;
    }

    private static AuthorizationStatus parseStatus(String code) {
        if (code == null || code.isBlank()) {
            return AuthorizationStatus.ENABLED;
        }
        AuthorizationStatus status = AuthorizationStatus.getStatus(code.trim());
        if (status == null) {
            throw AuthorizationErrors.badRequest("Unsupported policy status '" + code + "'");
        }
        return status;
    }

    private static String resolveVersion(PolicyRequest request) {
        if (request.getVersion() != null && !request.getVersion().isBlank()) {
            return request.getVersion().trim();
        }
        PolicyDocument document = request.getDocument();
        if (document != null && document.getVersion() != null && !document.getVersion().isBlank()) {
            return document.getVersion().trim();
        }
        return AuthorizationConstants.DEFAULT_POLICY_VERSION;
    }
}