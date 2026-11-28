package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.AuthzLogView;
import cn.tolim.pudans.iam.authorization.dto.EffectivePermissionResponse;
import cn.tolim.pudans.iam.authorization.dto.EffectivePolicyView;
import cn.tolim.pudans.iam.authorization.dto.MatchedStatementView;
import cn.tolim.pudans.iam.authorization.dto.PolicyEvaluateRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicyEvaluateResponse;
import cn.tolim.pudans.iam.authorization.dto.PolicySimulationRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicySimulationResponse;
import cn.tolim.pudans.iam.authorization.dto.RoleSummary;
import cn.tolim.pudans.iam.authorization.entity.AuthzLogEntity;
import cn.tolim.pudans.iam.authorization.entity.AuthzPolicyEntity;
import cn.tolim.pudans.iam.authorization.entity.AuthzRoleEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.PolicyDecision;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.repository.AuthzLogRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyRepository;
import cn.tolim.pudans.iam.infra.util.IpUtils;
import cn.tolim.pudans.iam.authorization.service.PolicyEvaluationEngine.CandidatePolicy;
import cn.tolim.pudans.iam.authorization.service.PrincipalPermissionResolver.EffectivePolicy;
import cn.tolim.pudans.iam.authorization.service.PrincipalPermissionResolver.EffectivePermissions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final PrincipalPermissionResolver permissionResolver;
    private final PolicyEvaluationEngine evaluationEngine;
    private final PolicyDocumentService policyDocumentService;
    private final AuthzPolicyRepository policyRepository;
    private final AuthzLogRepository authzLogRepository;
    private final AuthzDecisionCache decisionCache;
    private final ObjectMapper objectMapper;

    // Evaluates an authorization request and audits the decision
    // Parameter request: PolicyEvaluateRequest
    // Returns: PolicyEvaluateResponse
    public PolicyEvaluateResponse evaluate(PolicyEvaluateRequest request) {
        PrincipalType principalType = EnumSupport.principalType(request.getPrincipalType());
        validatePrincipalId(request.getPrincipalId());
        validateAction(request.getAppCode(), request.getAction());
        validateResource(request.getAppCode(), request.getResource());
        Map<String, Object> context = effectiveContext(request.getContext());
        PolicyEvaluateResponse response = evaluateInternal(principalType, request.getPrincipalId(),
                request.getAppCode().trim(), request.getAction().trim(), request.getResource(),
                request.getOrgId(), context);
        audit(principalType, request, context, response);
        return response;
    }

    // Evaluates several authorization requests, every decision is audited
    // Parameter requests: List<PolicyEvaluateRequest>
    // Returns: List<PolicyEvaluateResponse>
    public List<PolicyEvaluateResponse> evaluateBatch(List<PolicyEvaluateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw AuthorizationErrors.badRequest("requests must not be empty");
        }
        if (requests.size() > 100) {
            throw AuthorizationErrors.badRequest("A batch can contain at most 100 requests");
        }
        List<PolicyEvaluateResponse> responses = new ArrayList<>(requests.size());
        for (PolicyEvaluateRequest request : requests) {
            responses.add(evaluate(request));
        }
        return responses;
    }

    // Simulates a policy decision without changing permissions and without auditing
    // Parameter request: PolicySimulationRequest
    // Returns: PolicySimulationResponse
    public PolicySimulationResponse simulate(PolicySimulationRequest request) {
        validateAction(request.getAppCode(), request.getAction());
        validateResource(request.getAppCode(), request.getResource());
        List<CandidatePolicy> candidates = simulationCandidates(request);
        PolicyEvaluationEngine.Result result = evaluationEngine.evaluate(candidates,
                request.getAppCode().trim(), request.getAction().trim(), request.getResource(),
                request.getOrgId(), request.getContext());
        return toSimulationResponse(result);
    }

    // Returns the effective roles and policies of a principal
    // Parameter principalType: String principal type code
    // Parameter principalId: String principal ID
    // Parameter appCode: String optional application code used to filter scopes
    // Returns: EffectivePermissionResponse
    public EffectivePermissionResponse effectivePermissions(String principalType, String principalId,
                                                           String appCode) {
        PrincipalType type = EnumSupport.principalType(principalType);
        validatePrincipalId(principalId);
        EffectivePermissions effective = permissionResolver.resolve(type, principalId,
                trimToNull(appCode), null);
        EffectivePermissionResponse response = new EffectivePermissionResponse();
        response.setPrincipalType(type.getCode());
        response.setPrincipalId(principalId);
        for (AuthzRoleEntity role : effective.roles()) {
            RoleSummary summary = new RoleSummary();
            summary.setId(role.getId());
            summary.setCode(role.getCode());
            summary.setName(role.getName());
            summary.setStatus(role.getStatus() == null ? null : role.getStatus().getCode());
            summary.setDescription(role.getDescription());
            response.getRoles().add(summary);
        }
        for (EffectivePolicy effectivePolicy : effective.policies()) {
            EffectivePolicyView view = new EffectivePolicyView();
            view.setPolicyId(effectivePolicy.policy().getId());
            view.setPolicyCode(effectivePolicy.policy().getCode());
            view.setPolicyName(effectivePolicy.policy().getName());
            view.setScope(effectivePolicy.scope());
            if (effectivePolicy.source() != null) {
                view.setSourcePrincipalType(effectivePolicy.source().type().getCode());
                view.setSourcePrincipalId(effectivePolicy.source().id());
            }
            response.getPolicies().add(view);
            if (!response.getPolicyCodes().contains(effectivePolicy.policy().getCode())) {
                response.getPolicyCodes().add(effectivePolicy.policy().getCode());
            }
        }
        return response;
    }

    // Lists authorization decision logs
    // Parameter principalType: String optional principal type filter
    // Parameter principalId: String optional principal ID filter
    // Parameter appCode: String optional application filter
    // Parameter decision: String optional decision filter
    // Parameter limit: int maximum number of entries
    // Returns: List<AuthzLogView>
    public List<AuthzLogView> logs(String principalType, String principalId, String appCode,
                                   String decision, int limit) {
        int size = limit <= 0 ? 100 : Math.min(limit, 500);
        PrincipalType type = principalType == null || principalType.isBlank() ? null
                : EnumSupport.principalType(principalType);
        PolicyDecision policyDecision = parseDecision(decision);
        Specification<AuthzLogEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(builder.equal(root.get("principalType"), type));
            }
            if (principalId != null && !principalId.isBlank()) {
                predicates.add(builder.equal(root.get("principalId"), principalId.trim()));
            }
            if (appCode != null && !appCode.isBlank()) {
                predicates.add(builder.equal(root.get("appCode"), appCode.trim()));
            }
            if (policyDecision != null) {
                predicates.add(builder.equal(root.get("decision"), policyDecision));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return authzLogRepository.findAll(specification, pageable).getContent().stream()
                .map(this::toLogView).toList();
    }

    private PolicyEvaluateResponse evaluateInternal(PrincipalType principalType, String principalId,
                                                    String appCode, String action, String resource,
                                                    String orgId, Map<String, Object> context) {
        String cacheKey = buildCacheKey(principalType, principalId, appCode, action, resource, orgId,
                context);
        if (cacheKey != null) {
            Optional<PolicyEvaluateResponse> cached = decisionCache.get(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        EffectivePermissions effective = permissionResolver.resolve(principalType, principalId,
                appCode, resource);
        List<CandidatePolicy> candidates = new ArrayList<>();
        for (EffectivePolicy effectivePolicy : effective.policies()) {
            var document = policyDocumentService.parseOrNull(effectivePolicy.policy().getDocument());
            if (document != null) {
                candidates.add(new CandidatePolicy(effectivePolicy.policy().getCode(), document));
            }
        }
        PolicyEvaluationEngine.Result result = evaluationEngine.evaluate(candidates, appCode, action,
                resource, orgId, context);
        PolicyEvaluateResponse response = toResponse(result);
        if (cacheKey != null) {
            decisionCache.put(cacheKey, response);
        }
        return response;
    }

    private List<CandidatePolicy> simulationCandidates(PolicySimulationRequest request) {
        List<CandidatePolicy> candidates = new ArrayList<>();
        if (request.getPolicies() != null && !request.getPolicies().isEmpty()) {
            int index = 0;
            for (var document : request.getPolicies()) {
                policyDocumentService.validateOrThrow(document);
                candidates.add(new CandidatePolicy("inline[" + index + "]", document));
                index++;
            }
            return candidates;
        }
        boolean hasPrincipal = (request.getPrincipalType() != null
                && !request.getPrincipalType().isBlank())
                || (request.getPrincipalId() != null && !request.getPrincipalId().isBlank());
        if (hasPrincipal) {
            if (request.getPrincipalType() == null || request.getPrincipalType().isBlank()
                    || request.getPrincipalId() == null || request.getPrincipalId().isBlank()) {
                throw AuthorizationErrors.badRequest(
                        "principalType and principalId must both be provided to simulate a principal");
            }
            PrincipalType type = EnumSupport.principalType(request.getPrincipalType());
            EffectivePermissions effective = permissionResolver.resolve(type,
                    request.getPrincipalId(), request.getAppCode().trim(), request.getResource());
            for (EffectivePolicy effectivePolicy : effective.policies()) {
                var document = policyDocumentService.parseOrNull(effectivePolicy.policy().getDocument());
                if (document != null) {
                    candidates.add(new CandidatePolicy(effectivePolicy.policy().getCode(), document));
                }
            }
            return candidates;
        }
        for (AuthzPolicyEntity policy : policyRepository.findAllByStatus(AuthorizationStatus.ENABLED)) {
            var document = policyDocumentService.parseOrNull(policy.getDocument());
            if (document != null) {
                candidates.add(new CandidatePolicy(policy.getCode(), document));
            }
        }
        return candidates;
    }

    private PolicyEvaluateResponse toResponse(PolicyEvaluationEngine.Result result) {
        PolicyEvaluateResponse response = new PolicyEvaluateResponse();
        response.setDecision(result.decision().getCode());
        response.setAllowed(result.allowed());
        response.setReason(result.reason());
        response.setMatchedPolicies(result.matchedPolicies());
        response.setEvaluatedAt(LocalDateTime.now());
        return response;
    }

    private PolicySimulationResponse toSimulationResponse(PolicyEvaluationEngine.Result result) {
        PolicySimulationResponse response = new PolicySimulationResponse();
        response.setDecision(result.decision().getCode());
        response.setAllowed(result.allowed());
        response.setReason(result.reason());
        response.setMatchedPolicies(result.matchedPolicies());
        for (PolicyEvaluationEngine.Match match : result.matches()) {
            response.getMatchedStatements().add(new MatchedStatementView(match.policyCode(),
                    match.sid(), match.effect()));
        }
        response.setEvaluatedAt(LocalDateTime.now());
        return response;
    }

    private void audit(PrincipalType principalType, PolicyEvaluateRequest request,
                       Map<String, Object> context, PolicyEvaluateResponse response) {
        AuthzLogEntity entry = new AuthzLogEntity()
                .setPrincipalType(principalType)
                .setPrincipalId(request.getPrincipalId())
                .setAppCode(request.getAppCode())
                .setAction(request.getAction())
                .setResource(request.getResource())
                .setDecision(PolicyDecision.getDecision(response.getDecision()))
                .setReason(truncate(response.getReason()))
                .setContext(buildAuditContext(request, context, response));
        try {
            authzLogRepository.save(entry);
        } catch (RuntimeException e) {
            log.error("Failed to persist authorization decision audit: {}", e.getMessage(), e);
        }
    }

    private String buildAuditContext(PolicyEvaluateRequest request, Map<String, Object> context,
                                     PolicyEvaluateResponse response) {
        Map<String, Object> auditContext = new LinkedHashMap<>();
        if (request.getOrgId() != null && !request.getOrgId().isBlank()) {
            auditContext.put("orgId", request.getOrgId());
        }
        if (context != null) {
            auditContext.putAll(context);
        }
        if (response.getMatchedPolicies() != null) {
            auditContext.put("matchedPolicies", response.getMatchedPolicies());
        }
        try {
            return objectMapper.writeValueAsString(auditContext);
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize authorization audit context: {}", e.getMessage());
            return null;
        }
    }

    private AuthzLogView toLogView(AuthzLogEntity entity) {
        AuthzLogView view = new AuthzLogView();
        view.setId(entity.getId());
        view.setPrincipalType(entity.getPrincipalType() == null ? null
                : entity.getPrincipalType().getCode());
        view.setPrincipalId(entity.getPrincipalId());
        view.setAppCode(entity.getAppCode());
        view.setAction(entity.getAction());
        view.setResource(entity.getResource());
        view.setDecision(entity.getDecision() == null ? null : entity.getDecision().getCode());
        view.setReason(entity.getReason());
        view.setContext(entity.getContext());
        view.setCreatedAt(entity.getCreatedAt());
        return view;
    }

    private static String buildCacheKey(PrincipalType principalType, String principalId,
                                        String appCode, String action, String resource, String orgId,
                                        Map<String, Object> context) {
        String baseKey = principalType.getCode() + "|" + principalId + "|" + appCode + "|" + action
                + "|" + (resource == null ? "" : resource) + "|" + (orgId == null ? "" : orgId);
        if (context == null || context.isEmpty()) {
            return baseKey;
        }
        if (context.size() == 1 && context.get("ip") != null) {
            return baseKey + "|ip=" + context.get("ip");
        }
        return null;
    }

    // Adds the request IP to the evaluation context when the caller did not supply one
    // Parameter context: Map<String, Object> request context
    // Returns: Map<String, Object> effective context
    private static Map<String, Object> effectiveContext(Map<String, Object> context) {
        Map<String, Object> effective = new LinkedHashMap<>();
        if (context != null) {
            effective.putAll(context);
        }
        if (!effective.containsKey("ip")) {
            String ip = currentRequestIp();
            if (ip != null) {
                effective.put("ip", ip);
            }
        }
        return effective;
    }

    private static String currentRequestIp() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String ip = IpUtils.getIpAddr(attributes.getRequest());
                if (ip != null && !ip.isBlank() && !"Unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
            }
        } catch (RuntimeException ignored) {
            // The request context is not available outside a servlet request
        }
        return null;
    }

    private static PolicyDecision parseDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            return null;
        }
        PolicyDecision parsed = PolicyDecision.getDecision(decision.trim());
        if (parsed == null) {
            throw AuthorizationErrors.badRequest("Unsupported decision '" + decision + "'");
        }
        return parsed;
    }

    private static void validatePrincipalId(String principalId) {
        if (principalId == null || principalId.isBlank()) {
            throw AuthorizationErrors.badRequest("principalId must not be blank");
        }
    }

    private static void validateAction(String appCode, String action) {
        if (appCode == null || appCode.isBlank()) {
            throw AuthorizationErrors.badRequest("appCode must not be blank");
        }
        if (action == null || action.isBlank()) {
            throw AuthorizationErrors.badRequest("action must not be blank");
        }
        String prefix = appCode.trim() + ":";
        if (!action.startsWith(prefix) || action.length() == prefix.length()) {
            throw AuthorizationErrors.badRequest("action '" + action
                    + "' must be in {appCode}:{action} format and belong to '" + appCode.trim() + "'");
        }
    }

    private static void validateResource(String appCode, String resource) {
        if (resource == null || resource.isBlank()) {
            return;
        }
        String prefix = appCode.trim() + ":";
        if (!resource.startsWith(prefix) || resource.length() == prefix.length()) {
            throw AuthorizationErrors.badRequest("resource '" + resource
                    + "' must be in {appCode}:{resourceType}/{resourceId} format and belong to '"
                    + appCode.trim() + "'");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= AuthorizationConstants.AUDIT_REASON_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, AuthorizationConstants.AUDIT_REASON_MAX_LENGTH);
    }
}