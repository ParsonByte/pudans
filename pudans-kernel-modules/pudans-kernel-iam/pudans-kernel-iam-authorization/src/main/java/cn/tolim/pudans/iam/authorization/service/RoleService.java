package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.RoleBindingRequest;
import cn.tolim.pudans.iam.authorization.dto.RoleBindingView;
import cn.tolim.pudans.iam.authorization.dto.RoleRequest;
import cn.tolim.pudans.iam.authorization.dto.RoleSummary;
import cn.tolim.pudans.iam.authorization.entity.AuthzRoleBindingEntity;
import cn.tolim.pudans.iam.authorization.entity.AuthzRoleEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyAttachmentRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzRoleBindingRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzRoleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class RoleService {

    private final AuthzRoleRepository roleRepository;
    private final AuthzRoleBindingRepository roleBindingRepository;
    private final AuthzPolicyAttachmentRepository policyAttachmentRepository;
    private final PrincipalValidator principalValidator;
    private final AuthzDecisionCache decisionCache;
    private final ObjectMapper objectMapper;

    // Creates a role
    // Parameter request: RoleRequest
    // Returns: RoleSummary
    @Transactional(rollbackFor = Exception.class)
    public RoleSummary create(RoleRequest request) {
        String code = request.getCode().trim();
        if (roleRepository.existsByCode(code)) {
            throw AuthorizationErrors.conflict("Role code '" + code + "' already exists");
        }
        validateTrustPolicy(request.getTrustPolicy());
        AuthzRoleEntity entity = new AuthzRoleEntity()
                .setCode(code)
                .setName(request.getName().trim())
                .setTrustPolicy(blankToNull(request.getTrustPolicy()))
                .setStatus(parseStatus(request.getStatus()))
                .setDescription(request.getDescription());
        EntityAuditSupport.stampCreate(entity);
        RoleSummary summary = toSummary(roleRepository.save(entity));
        decisionCache.invalidateAll();
        return summary;
    }

    // Updates a role
    // Parameter id: String role ID
    // Parameter request: RoleRequest
    // Returns: RoleSummary
    @Transactional(rollbackFor = Exception.class)
    public RoleSummary update(String id, RoleRequest request) {
        AuthzRoleEntity entity = requireRole(id);
        String code = request.getCode().trim();
        if (!code.equals(entity.getCode()) && roleRepository.existsByCode(code)) {
            throw AuthorizationErrors.conflict("Role code '" + code + "' already exists");
        }
        validateTrustPolicy(request.getTrustPolicy());
        entity.setCode(code)
                .setName(request.getName().trim())
                .setTrustPolicy(blankToNull(request.getTrustPolicy()))
                .setStatus(parseStatus(request.getStatus()))
                .setDescription(request.getDescription());
        EntityAuditSupport.stampUpdate(entity);
        RoleSummary summary = toSummary(roleRepository.save(entity));
        decisionCache.invalidateAll();
        return summary;
    }

    // Deletes a role and every binding or attachment that references it
    // Parameter id: String role ID
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AuthzRoleEntity entity = requireRole(id);
        roleBindingRepository.deleteAllByRoleId(id);
        roleBindingRepository.deleteAllByPrincipalTypeAndPrincipalId(PrincipalType.ROLE, id);
        policyAttachmentRepository.deleteAllByPrincipalTypeAndPrincipalId(PrincipalType.ROLE, id);
        roleRepository.delete(entity);
        decisionCache.invalidateAll();
    }

    // Returns a role summary
    // Parameter id: String role ID
    // Returns: RoleSummary
    public RoleSummary get(String id) {
        return toSummary(requireRole(id));
    }

    // Lists roles filtered by status and keyword
    // Parameter status: AuthorizationStatus optional status filter
    // Parameter keyword: String optional name or code keyword
    // Returns: List<RoleSummary>
    public List<RoleSummary> list(AuthorizationStatus status, String keyword) {
        return roleRepository.findAll().stream()
                .filter(role -> status == null || status.equals(role.getStatus()))
                .filter(role -> matchesKeyword(role, keyword))
                .map(this::toSummary)
                .toList();
    }

    // Binds a role to a principal
    // Parameter request: RoleBindingRequest
    // Returns: RoleBindingView
    @Transactional(rollbackFor = Exception.class)
    public RoleBindingView bind(RoleBindingRequest request) {
        AuthzRoleEntity role = requireRole(request.getRoleId());
        PrincipalType principalType = principalValidator.parseType(request.getPrincipalType());
        principalValidator.validate(principalType, request.getPrincipalId());
        if (PrincipalType.ROLE == principalType && role.getId().equals(request.getPrincipalId())) {
            throw AuthorizationErrors.badRequest("A role cannot be bound to itself");
        }
        String scope = normalizeScope(request.getScope());
        if (roleBindingRepository.existsByRoleIdAndPrincipalTypeAndPrincipalIdAndScope(
                role.getId(), principalType, request.getPrincipalId(), scope)) {
            throw AuthorizationErrors.conflict("Role '" + role.getCode()
                    + "' is already bound to principal '" + request.getPrincipalId()
                    + "' in scope '" + scope + "'");
        }
        AuthzRoleBindingEntity entity = new AuthzRoleBindingEntity()
                .setRoleId(role.getId())
                .setPrincipalType(principalType)
                .setPrincipalId(request.getPrincipalId())
                .setScope(scope);
        EntityAuditSupport.stampCreate(entity);
        AuthzRoleBindingEntity saved = roleBindingRepository.save(entity);
        decisionCache.invalidateAll();
        return toBindingView(saved, role.getCode());
    }

    // Removes a role binding
    // Parameter id: String binding ID
    @Transactional(rollbackFor = Exception.class)
    public void unbind(String id) {
        AuthzRoleBindingEntity entity = roleBindingRepository.findById(id)
                .orElseThrow(() -> AuthorizationErrors.notFound("Role binding '" + id
                        + "' does not exist"));
        roleBindingRepository.delete(entity);
        decisionCache.invalidateAll();
    }

    // Lists bindings of a role
    // Parameter roleId: String role ID
    // Returns: List<RoleBindingView>
    public List<RoleBindingView> listBindingsByRole(String roleId) {
        AuthzRoleEntity role = requireRole(roleId);
        return roleBindingRepository.findAllByRoleId(roleId).stream()
                .map(binding -> toBindingView(binding, role.getCode())).toList();
    }

    // Lists bindings of a principal
    // Parameter principalType: PrincipalType
    // Parameter principalId: String principal ID
    // Returns: List<RoleBindingView>
    public List<RoleBindingView> listBindingsByPrincipal(PrincipalType principalType,
                                                         String principalId) {
        List<RoleBindingView> views = new ArrayList<>();
        for (AuthzRoleBindingEntity binding : roleBindingRepository
                .findAllByPrincipalTypeAndPrincipalId(principalType, principalId)) {
            String roleCode = requireRole(binding.getRoleId()).getCode();
            views.add(toBindingView(binding, roleCode));
        }
        return views;
    }

    // Returns the role with the given ID or throws when it does not exist
    // Parameter id: String role ID
    // Returns: AuthzRoleEntity
    public AuthzRoleEntity requireRole(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> AuthorizationErrors.notFound("Role '" + id + "' does not exist"));
    }

    // Maps a role entity to a summary
    // Parameter entity: AuthzRoleEntity
    // Returns: RoleSummary
    public RoleSummary toSummary(AuthzRoleEntity entity) {
        RoleSummary summary = new RoleSummary();
        summary.setId(entity.getId());
        summary.setCode(entity.getCode());
        summary.setName(entity.getName());
        summary.setStatus(entity.getStatus() == null ? null : entity.getStatus().getCode());
        summary.setDescription(entity.getDescription());
        return summary;
    }

    private static boolean matchesKeyword(AuthzRoleEntity role, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String value = keyword.trim().toLowerCase();
        return (role.getCode() != null && role.getCode().toLowerCase().contains(value))
                || (role.getName() != null && role.getName().toLowerCase().contains(value));
    }

    private void validateTrustPolicy(String trustPolicy) {
        if (trustPolicy == null || trustPolicy.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(trustPolicy);
        } catch (JsonProcessingException e) {
            throw AuthorizationErrors.badRequest("Invalid trust policy JSON: " + e.getOriginalMessage());
        }
    }

    private static AuthorizationStatus parseStatus(String code) {
        if (code == null || code.isBlank()) {
            return AuthorizationStatus.ENABLED;
        }
        AuthorizationStatus status = AuthorizationStatus.getStatus(code.trim());
        if (status == null) {
            throw AuthorizationErrors.badRequest("Unsupported role status '" + code + "'");
        }
        return status;
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return AuthorizationConstants.SCOPE_ALL;
        }
        return scope.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static RoleBindingView toBindingView(AuthzRoleBindingEntity entity, String roleCode) {
        RoleBindingView view = new RoleBindingView();
        view.setId(entity.getId());
        view.setRoleId(entity.getRoleId());
        view.setRoleCode(roleCode);
        view.setPrincipalType(entity.getPrincipalType() == null ? null
                : entity.getPrincipalType().getCode());
        view.setPrincipalId(entity.getPrincipalId());
        view.setScope(entity.getScope());
        return view;
    }
}