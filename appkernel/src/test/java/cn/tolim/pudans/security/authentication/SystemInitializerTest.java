package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.rbac.BuiltinRoleDefinitions;
import cn.tolim.pudans.core.rbac.SuperAdminResolver;
import cn.tolim.pudans.extension.contract.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SystemInitializerTest {

    @Mock
    InternalReactiveExtensionClient client;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    SuperAdminResolver superAdminResolver;

    @Mock
    DomainEventPublisher domainEventPublisher;

    @Mock
    BuiltinRoleDefinitions builtinRoleDefinitions;

    @Test
    void createsTheSuperAdminWithoutAnEmail() {
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        var initializer = new SystemInitializer(
                client, passwordEncoder, superAdminResolver, domainEventPublisher, builtinRoleDefinitions);
        var param = SuperAdminInitializer.InitializationParam.builder()
                .username("admin")
                .password("password")
                .build();

        var user = initializer.createAdmin(param);

        assertThat(user.getMetadata().getName()).isEqualTo("admin");
        assertThat(user.getSpec().getEmail()).isNull();
        assertThat(user.getSpec().getPassword()).isEqualTo("encoded-password");
    }

    // 管理员创建时就带系统终结器，外部创建入口会拒绝受保护字段
    @Test
    void createsTheSuperAdminThroughTheInternalClient() {
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(client.fetch(User.class, "admin")).thenReturn(Mono.empty());
        when(client.createInternal(any(User.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var initializer = new SystemInitializer(
                client, passwordEncoder, superAdminResolver, domainEventPublisher, builtinRoleDefinitions);
        var param = SuperAdminInitializer.InitializationParam.builder()
                .username("admin")
                .password("password")
                .build();

        var user = initializer.ensureSuperAdmin(param).block();

        assertThat(user.getMetadata().getFinalizers()).contains(MetadataUtil.SYSTEM_FINALIZER);
        verify(client, never()).create(any());
    }
}
