package cn.tolim.pudans.iam.authorization.configuration;

import cn.tolim.pudans.iam.authorization.repository.AuthzLogRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyAttachmentRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzRoleBindingRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzRoleRepository;
import cn.tolim.pudans.iam.authorization.repository.ResourceTypeRepository;
import cn.tolim.pudans.iam.authorization.repository.ServiceActionRepository;
import cn.tolim.pudans.iam.authorization.service.AuthzDecisionCache;
import cn.tolim.pudans.iam.authorization.service.AuthorizationService;
import cn.tolim.pudans.iam.authorization.service.PolicyAttachmentService;
import cn.tolim.pudans.iam.authorization.service.PolicyDocumentService;
import cn.tolim.pudans.iam.authorization.service.PolicyEvaluationEngine;
import cn.tolim.pudans.iam.authorization.service.PolicyService;
import cn.tolim.pudans.iam.authorization.service.PrincipalPermissionResolver;
import cn.tolim.pudans.iam.authorization.service.PrincipalValidator;
import cn.tolim.pudans.iam.authorization.service.ResourceTypeService;
import cn.tolim.pudans.iam.authorization.service.RoleService;
import cn.tolim.pudans.iam.authorization.service.ServiceActionService;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


class AuthorizationContextTests {

    @Test
    void authorizationServicesAndJackson2MapperWireTogether() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AuthzPolicyRepository.class, () -> mock(AuthzPolicyRepository.class));
            context.registerBean(AuthzPolicyAttachmentRepository.class,
                    () -> mock(AuthzPolicyAttachmentRepository.class));
            context.registerBean(AuthzRoleRepository.class, () -> mock(AuthzRoleRepository.class));
            context.registerBean(AuthzRoleBindingRepository.class,
                    () -> mock(AuthzRoleBindingRepository.class));
            context.registerBean(AuthzLogRepository.class, () -> mock(AuthzLogRepository.class));
            context.registerBean(ServiceActionRepository.class,
                    () -> mock(ServiceActionRepository.class));
            context.registerBean(ResourceTypeRepository.class,
                    () -> mock(ResourceTypeRepository.class));
            context.registerBean(AppRepository.class, () -> mock(AppRepository.class));
            context.registerBean(UserRepository.class, () -> mock(UserRepository.class));
            context.registerBean(UserGroupRepository.class, () -> mock(UserGroupRepository.class));
            context.registerBean(OrganizationRepository.class,
                    () -> mock(OrganizationRepository.class));
            context.registerBean(UserGroupMemberRepository.class,
                    () -> mock(UserGroupMemberRepository.class));
            context.registerBean(OrganizationMemberRepository.class,
                    () -> mock(OrganizationMemberRepository.class));

            context.register(AuthorizationJacksonConfiguration.class,
                    PolicyDocumentService.class, PrincipalValidator.class,
                    PrincipalPermissionResolver.class, PolicyEvaluationEngine.class,
                    AuthzDecisionCache.class, PolicyService.class, PolicyAttachmentService.class,
                    RoleService.class, ServiceActionService.class, ResourceTypeService.class,
                    AuthorizationService.class);
            context.refresh();

            assertThat(context.getBean(ObjectMapper.class)).isNotNull();
            assertThat(context.getBean(AuthorizationService.class)).isNotNull();
            assertThat(context.getBean(PolicyService.class)).isNotNull();
            assertThat(context.getBean(RoleService.class)).isNotNull();
            assertThat(context.getBean(ServiceActionService.class)).isNotNull();
            assertThat(context.getBean(ResourceTypeService.class)).isNotNull();
            assertThat(context.getBean(PolicyAttachmentService.class)).isNotNull();
        }
    }
}