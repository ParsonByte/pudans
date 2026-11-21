package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;

@ExtendWith(MockitoExtension.class)
class SuperAdminResolverTest {

    @Mock
    ReactiveExtensionClient reactiveClient;

    @Mock
    ExtensionClient client;

    @Mock
    InternalReactiveExtensionClient internalClient;

    SuperAdminResolver resolver;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        resolver = new SuperAdminResolver(reactiveClient, client, internalClient);
    }

    private static UserGroupBinding canonicalBinding(String username) {
        var binding = UserGroupBinding.membership(username, BuiltinEntities.SUPER_ADMINS_GROUP);
        var metadata = new Metadata();
        metadata.setName(SuperAdminResolver.bindingName(username));
        binding.setMetadata(metadata);
        return binding;
    }

    @Test
    void identityIsDerivedFromTheDeterministicBindingName() {
        when(reactiveClient.fetch(UserGroupBinding.class, "alice-super-admins-binding"))
                .thenReturn(Mono.just(canonicalBinding("alice")));
        when(client.fetch(UserGroupBinding.class, "alice-super-admins-binding"))
                .thenReturn(Optional.of(canonicalBinding("alice")));

        StepVerifier.create(resolver.hasSuperAdminBinding("alice"))
                .expectNext(true)
                .verifyComplete();
        assertThat(resolver.hasCanonicalBindingBlocking("alice")).isTrue();
        StepVerifier.create(resolver.isCanonicalSuperAdmin("alice"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void userWithoutBindingIsNotSuperAdminEvenWithTheLegacyLabel() {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName("mallory");
        metadata.setLabels(Map.of("pudans.tolim.cn/super-admin", "true"));
        user.setMetadata(metadata);
        assertThat(user.getMetadata().getLabels()).containsKey("pudans.tolim.cn/super-admin");

        when(reactiveClient.fetch(UserGroupBinding.class, "mallory-super-admins-binding"))
                .thenReturn(Mono.empty());

        StepVerifier.create(resolver.hasSuperAdminBinding("mallory"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(resolver.isCanonicalSuperAdmin("mallory"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void tamperedBindingStillProtectsTheAccountUntilItIsRepaired() {
        var tampered = canonicalBinding("alice");
        tampered.setRoleRef(UserGroupBinding.RoleRef.userGroup("normal-users"));
        when(reactiveClient.fetch(UserGroupBinding.class, "alice-super-admins-binding"))
                .thenReturn(Mono.just(tampered));
        when(client.fetch(UserGroupBinding.class, "alice-super-admins-binding"))
                .thenReturn(Optional.of(tampered));

        StepVerifier.create(resolver.hasSuperAdminBinding("alice"))
                .expectNext(true)
                .verifyComplete();
        assertThat(resolver.hasCanonicalBindingBlocking("alice")).isFalse();
        StepVerifier.create(resolver.isCanonicalSuperAdmin("alice"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void lookupErrorIsPropagatedInsteadOfFailingOpen() {
        when(reactiveClient.fetch(UserGroupBinding.class, "alice-super-admins-binding"))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(resolver.hasSuperAdminBinding("alice"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void canonicalBindingRequiresExactlyTheOwnerAsSubject() {
        var binding = canonicalBinding("alice");
        binding.setSubjects(new java.util.LinkedList<>(java.util.List.of(
                UserGroupBinding.Subject.user("alice"), UserGroupBinding.Subject.user("bob"))));

        assertThat(resolver.isCanonicalBinding(binding)).isFalse();
        assertThat(resolver.ownerOf(binding)).isEqualTo("alice");
        assertThat(resolver.isSuperAdminBindingName("alice-super-admins-binding")).isTrue();
        assertThat(resolver.isSuperAdminBindingName("alice-other-binding")).isFalse();
    }

    @Test
    void bootstrapCreatesTheCanonicalBindingThroughTheInternalClient() {
        when(internalClient.fetch(UserGroupBinding.class, "alice-super-admins-binding"))
                .thenReturn(Mono.empty());
        when(internalClient.createInternal(any(UserGroupBinding.class)))
                .thenReturn(Mono.just(canonicalBinding("alice")));
        when(reactiveClient.listAll(eq(UserGroupBinding.class), any(ListOptions.class), any()))
                .thenReturn(Flux.empty());

        StepVerifier.create(resolver.initialize("alice")).verifyComplete();

        var captor = ArgumentCaptor.forClass(UserGroupBinding.class);
        verify(internalClient).createInternal(captor.capture());
        var created = captor.getValue();
        assertThat(created.getMetadata().getName()).isEqualTo("alice-super-admins-binding");
        assertThat(created.getRoleRef().getName()).isEqualTo(BuiltinEntities.SUPER_ADMINS_GROUP);
        assertThat(created.getSubjects()).hasSize(1);
        assertThat(created.getSubjects().get(0).getName()).isEqualTo("alice");
    }
}
