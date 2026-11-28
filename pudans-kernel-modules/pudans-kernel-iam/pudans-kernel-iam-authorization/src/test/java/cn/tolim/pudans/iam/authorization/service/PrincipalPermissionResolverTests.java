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
import cn.tolim.pudans.iam.authorization.service.PrincipalPermissionResolver.EffectivePermissions;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserGroupMemberEntity;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class PrincipalPermissionResolverTests {

    @Mock
    private UserGroupMemberRepository userGroupMemberRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private AuthzRoleBindingRepository roleBindingRepository;

    @Mock
    private AuthzPolicyAttachmentRepository policyAttachmentRepository;

    @Mock
    private AuthzRoleRepository roleRepository;

    @Mock
    private AuthzPolicyRepository policyRepository;

    private PrincipalPermissionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PrincipalPermissionResolver(userGroupMemberRepository,
                organizationMemberRepository, roleBindingRepository, policyAttachmentRepository,
                roleRepository, policyRepository);
    }

    @Test
    void userPermissionsIncludeDirectGroupRoleAndOrganizationPolicies() {
        UserGroupMemberEntity groupMember = new UserGroupMemberEntity();
        groupMember.setGroupId("group-1");
        when(userGroupMemberRepository.findByUserId("user-1")).thenReturn(List.of(groupMember));
        OrganizationMemberEntity orgMember = new OrganizationMemberEntity("org-1", "user-1");
        when(organizationMemberRepository.findAllByUserId("user-1")).thenReturn(List.of(orgMember));

        when(roleBindingRepository.findAllByPrincipalTypeAndPrincipalId(any(), any()))
                .thenAnswer(invocation -> {
                    PrincipalType type = invocation.getArgument(0);
                    String id = invocation.getArgument(1);
                    if (PrincipalType.USER == type && "user-1".equals(id)) {
                        return List.of(roleBinding("role-1"));
                    }
                    if (PrincipalType.USER_GROUP == type && "group-1".equals(id)) {
                        return List.of(roleBinding("role-2"));
                    }
                    return List.of();
                });

        AuthzRoleEntity role1 = role("role-1", "order-viewer", AuthorizationStatus.ENABLED);
        AuthzRoleEntity role2 = role("role-2", "order-operator", AuthorizationStatus.ENABLED);
        when(roleRepository.findAllById(any())).thenReturn(List.of(role1, role2));

        AuthzPolicyEntity direct = policy("policy-1", "DirectPolicy", AuthorizationStatus.ENABLED);
        AuthzPolicyEntity group = policy("policy-2", "GroupPolicy", AuthorizationStatus.ENABLED);
        AuthzPolicyEntity role = policy("policy-3", "RolePolicy", AuthorizationStatus.ENABLED);
        AuthzPolicyEntity disabled = policy("policy-4", "DisabledPolicy",
                AuthorizationStatus.DISABLED);
        when(policyAttachmentRepository.findAllByPrincipalTypeAndPrincipalId(any(), any()))
                .thenAnswer(invocation -> {
                    PrincipalType type = invocation.getArgument(0);
                    String id = invocation.getArgument(1);
                    if (PrincipalType.USER == type && "user-1".equals(id)) {
                        return List.of(attachment("policy-1", "order"));
                    }
                    if (PrincipalType.USER_GROUP == type && "group-1".equals(id)) {
                        return List.of(attachment("policy-2", "*"), attachment("policy-4", "*"));
                    }
                    if (PrincipalType.ROLE == type && "role-1".equals(id)) {
                        return List.of(attachment("policy-3", "order"));
                    }
                    return List.of();
                });
        when(policyRepository.findAllById(any())).thenReturn(List.of(direct, group, role, disabled));

        EffectivePermissions effective = resolver.resolve(PrincipalType.USER, "user-1", "order",
                "order:order/1");

        assertThat(effective.roles()).extracting(AuthzRoleEntity::getCode)
                .containsExactlyInAnyOrder("order-viewer", "order-operator");
        assertThat(effective.policies()).extracting(entry -> entry.policy().getCode())
                .containsExactlyInAnyOrder("DirectPolicy", "GroupPolicy", "RolePolicy");
        assertThat(effective.policies()).noneMatch(entry -> "DisabledPolicy".equals(
                entry.policy().getCode()));
        assertThat(effective.principals()).extracting(entry -> entry.type().getCode() + ":"
                + entry.id()).contains("USER:user-1", "USER_GROUP:group-1",
                "ORGANIZATION:org-1", "ROLE:role-1", "ROLE:role-2");
    }

    @Test
    void scopedAttachmentOutsideApplicationIsIgnored() {
        when(roleBindingRepository.findAllByPrincipalTypeAndPrincipalId(PrincipalType.USER, "user-1"))
                .thenReturn(List.of());
        when(policyAttachmentRepository.findAllByPrincipalTypeAndPrincipalId(PrincipalType.USER,
                "user-1")).thenReturn(List.of(attachment("policy-1", "billing")));

        EffectivePermissions effective = resolver.resolve(PrincipalType.USER, "user-1", "order",
                "order:order/1");

        assertThat(effective.policies()).isEmpty();
    }

    @Test
    void roleBindingScopedToAnotherApplicationDoesNotGrantPolicies() {
        when(roleBindingRepository.findAllByPrincipalTypeAndPrincipalId(any(), any()))
                .thenReturn(List.of(roleBinding("role-1", "billing")));
        when(roleRepository.findAllById(any())).thenReturn(List.of(role("role-1",
                "billing-operator", AuthorizationStatus.ENABLED)));

        EffectivePermissions order = resolver.resolve(PrincipalType.USER, "user-1", "order",
                "order:order/1");
        EffectivePermissions billing = resolver.resolve(PrincipalType.USER, "user-1", "billing",
                "billing:record/1");

        assertThat(order.roles()).isEmpty();
        assertThat(order.policies()).isEmpty();
        assertThat(billing.roles()).extracting(AuthzRoleEntity::getCode)
                .containsExactly("billing-operator");
    }

    @Test
    void disabledRoleDoesNotContributePolicies() {
        when(roleBindingRepository.findAllByPrincipalTypeAndPrincipalId(PrincipalType.USER, "user-1"))
                .thenReturn(List.of(roleBinding("role-1")));
        when(roleRepository.findAllById(any())).thenReturn(List.of(role("role-1", "disabled",
                AuthorizationStatus.DISABLED)));

        EffectivePermissions effective = resolver.resolve(PrincipalType.USER, "user-1", "order",
                "order:order/1");

        assertThat(effective.roles()).isEmpty();
        assertThat(effective.policies()).isEmpty();
    }

    private static AuthzRoleBindingEntity roleBinding(String roleId) {
        return roleBinding(roleId, "*");
    }

    private static AuthzRoleBindingEntity roleBinding(String roleId, String scope) {
        AuthzRoleBindingEntity binding = new AuthzRoleBindingEntity();
        binding.setRoleId(roleId);
        binding.setScope(scope);
        return binding;
    }

    private static AuthzRoleEntity role(String id, String code, AuthorizationStatus status) {
        AuthzRoleEntity role = new AuthzRoleEntity();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        role.setStatus(status);
        return role;
    }

    private static AuthzPolicyEntity policy(String id, String code, AuthorizationStatus status) {
        AuthzPolicyEntity policy = new AuthzPolicyEntity();
        policy.setId(id);
        policy.setCode(code);
        policy.setName(code);
        policy.setStatus(status);
        policy.setDocument("{\"version\":\"1.0\",\"statement\":[]}");
        return policy;
    }

    private static AuthzPolicyAttachmentEntity attachment(String policyId, String scope) {
        AuthzPolicyAttachmentEntity attachment = new AuthzPolicyAttachmentEntity();
        attachment.setPolicyId(policyId);
        attachment.setScope(scope);
        return attachment;
    }
}