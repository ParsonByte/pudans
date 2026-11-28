package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.entity.AuthzPolicyAttachmentEntity;
import cn.tolim.pudans.iam.authorization.entity.AuthzPolicyEntity;
import cn.tolim.pudans.iam.authorization.entity.AuthzRoleBindingEntity;
import cn.tolim.pudans.iam.authorization.entity.AuthzRoleEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyAttachmentRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzRoleBindingRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzRoleRepository;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserGroupMemberEntity;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
@RequiredArgsConstructor
public class PrincipalPermissionResolver {

    private final UserGroupMemberRepository userGroupMemberRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final AuthzRoleBindingRepository roleBindingRepository;
    private final AuthzPolicyAttachmentRepository policyAttachmentRepository;
    private final AuthzRoleRepository roleRepository;
    private final AuthzPolicyRepository policyRepository;

    // Resolves the effective roles and policies of a principal, including groups and roles
    // Parameter type: PrincipalType requested principal type
    // Parameter principalId: String requested principal ID
    // Parameter appCode: String application code used to filter attachment scopes
    // Parameter resource: String requested resource used to filter attachment scopes
    // Returns: EffectivePermissions
    public EffectivePermissions resolve(PrincipalType type, String principalId, String appCode,
                                        String resource) {
        Map<String, PrincipalRef> principals = new LinkedHashMap<>();
        addPrincipal(principals, type, principalId);
        if (PrincipalType.USER == type) {
            List<UserGroupMemberEntity> groupMembers = userGroupMemberRepository.findByUserId(principalId);
            if (groupMembers != null) {
                for (UserGroupMemberEntity member : groupMembers) {
                    addPrincipal(principals, PrincipalType.USER_GROUP, member.getGroupId());
                }
            }
            List<OrganizationMemberEntity> organizationMembers =
                    organizationMemberRepository.findAllByUserId(principalId);
            if (organizationMembers != null) {
                for (OrganizationMemberEntity member : organizationMembers) {
                    addPrincipal(principals, PrincipalType.ORGANIZATION, member.getOrgId());
                }
            }
        }
        Set<String> roleIds = collectRoleIds(new ArrayList<>(principals.values()), appCode, resource);
        List<AuthzRoleEntity> roles = loadEnabledRoles(roleIds);
        List<PrincipalRef> allPrincipals = new ArrayList<>(principals.values());
        for (AuthzRoleEntity role : roles) {
            PrincipalRef roleRef = new PrincipalRef(PrincipalType.ROLE, role.getId());
            if (!principals.containsKey(roleRef.key())) {
                principals.put(roleRef.key(), roleRef);
                allPrincipals.add(roleRef);
            }
        }
        Map<String, EffectivePolicy> effectivePolicies = new LinkedHashMap<>();
        for (PrincipalRef ref : allPrincipals) {
            List<AuthzPolicyAttachmentEntity> attachments = policyAttachmentRepository
                    .findAllByPrincipalTypeAndPrincipalId(ref.type(), ref.id());
            if (attachments == null) {
                continue;
            }
            for (AuthzPolicyAttachmentEntity attachment : attachments) {
                if (!scopeMatches(attachment.getScope(), appCode, resource)) {
                    continue;
                }
                if (attachment.getPolicyId() != null) {
                    effectivePolicies.putIfAbsent(attachment.getPolicyId(),
                            new EffectivePolicy(null, attachment.getScope(), ref));
                }
            }
        }
        Map<String, EffectivePolicy> resolvedPolicies = new LinkedHashMap<>();
        if (!effectivePolicies.isEmpty()) {
            Map<String, AuthzPolicyEntity> storedPolicies = new HashMap<>();
            for (AuthzPolicyEntity policy : policyRepository.findAllById(effectivePolicies.keySet())) {
                if (AuthorizationStatus.ENABLED.equals(policy.getStatus())) {
                    storedPolicies.put(policy.getId(), policy);
                }
            }
            effectivePolicies.forEach((policyId, effective) -> {
                AuthzPolicyEntity policy = storedPolicies.get(policyId);
                if (policy != null) {
                    resolvedPolicies.put(policyId,
                            new EffectivePolicy(policy, effective.scope(), effective.source()));
                }
            });
        }
        return new EffectivePermissions(List.copyOf(allPrincipals), roles,
                new ArrayList<>(resolvedPolicies.values()));
    }

    // Collects role IDs granted to any of the principals, following role to role bindings
    // Parameter principals: List<PrincipalRef> initial principals
    // Parameter appCode: String application code used to filter binding scopes
    // Parameter resource: String requested resource used to filter binding scopes
    // Returns: Set<String> role IDs
    private Set<String> collectRoleIds(List<PrincipalRef> principals, String appCode,
                                       String resource) {
        Set<String> roleIds = new LinkedHashSet<>();
        Deque<PrincipalRef> queue = new ArrayDeque<>(principals);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 10_000) {
            PrincipalRef ref = queue.poll();
            List<AuthzRoleBindingEntity> bindings = roleBindingRepository
                    .findAllByPrincipalTypeAndPrincipalId(ref.type(), ref.id());
            if (bindings == null) {
                continue;
            }
            for (AuthzRoleBindingEntity binding : bindings) {
                if (binding.getRoleId() != null
                        && scopeMatches(binding.getScope(), appCode, resource)
                        && roleIds.add(binding.getRoleId())) {
                    queue.add(new PrincipalRef(PrincipalType.ROLE, binding.getRoleId()));
                }
            }
        }
        return roleIds;
    }

    private List<AuthzRoleEntity> loadEnabledRoles(Set<String> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<AuthzRoleEntity> roles = new ArrayList<>();
        for (AuthzRoleEntity role : roleRepository.findAllById(roleIds)) {
            if (AuthorizationStatus.ENABLED.equals(role.getStatus())) {
                roles.add(role);
            }
        }
        return roles;
    }

    private static void addPrincipal(Map<String, PrincipalRef> principals, PrincipalType type,
                                     String id) {
        if (type == null || id == null || id.isBlank()) {
            return;
        }
        PrincipalRef ref = new PrincipalRef(type, id);
        principals.putIfAbsent(ref.key(), ref);
    }

    // Checks whether an attachment scope applies to the requested application and resource
    // Parameter scope: String attachment scope
    // Parameter appCode: String application code
    // Parameter resource: String requested resource
    // Returns: boolean
    public static boolean scopeMatches(String scope, String appCode, String resource) {
        if (scope == null || scope.isBlank() || "*".equals(scope.trim())) {
            return true;
        }
        String trimmed = scope.trim();
        if (appCode != null && trimmed.equals(appCode)) {
            return true;
        }
        if (resource != null && !resource.isBlank()) {
            if (trimmed.equals(resource)) {
                return true;
            }
            if (GlobMatcher.isWildcard(trimmed) && GlobMatcher.matches(trimmed, resource)) {
                return true;
            }
            if (!GlobMatcher.isWildcard(trimmed)) {
                String prefix = trimmed.endsWith("/") ? trimmed : trimmed + "/";
                if (resource.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return appCode != null && GlobMatcher.isWildcard(trimmed)
                && GlobMatcher.matches(trimmed, appCode);
    }

    // Principal reference used during permission resolution
    public record PrincipalRef(PrincipalType type, String id) {

        // Unique key of the principal reference
        // Returns: String
        public String key() {
            return type.getCode() + ":" + id;
        }
    }

    // Policy granted through an attachment
    public record EffectivePolicy(AuthzPolicyEntity policy, String scope, PrincipalRef source) {
    }

    // Effective permissions of a principal
    public record EffectivePermissions(List<PrincipalRef> principals, List<AuthzRoleEntity> roles,
                                       List<EffectivePolicy> policies) {
    }
}