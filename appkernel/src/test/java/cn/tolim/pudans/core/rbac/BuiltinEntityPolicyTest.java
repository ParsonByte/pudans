package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class BuiltinEntityPolicyTest {

    private static final String NAME = "alice";

    @Mock
    ReactiveExtensionClient client;

    @Mock
    SuperAdminResolver superAdminResolver;

    private BuiltinEntityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new BuiltinEntityPolicy(client, superAdminResolver);
    }

    private static GroupVersionKind userGvk() {
        return new GroupVersionKind(User.GROUP, User.VERSION, User.KIND);
    }

    private static GroupVersionKind roleGvk() {
        return new GroupVersionKind(Role.GROUP, Role.VERSION, Role.KIND);
    }

    private static Role role(String name, String dependencies) {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName(name);
        if (dependencies != null) {
            metadata.setAnnotations(Map.of(Role.ROLE_DEPENDENCIES_ANNO, dependencies));
        }
        role.setMetadata(metadata);
        role.setRules(List.of());
        return role;
    }

    private static Role template(String name) {
        var role = role(name, null);
        role.getMetadata().setLabels(Map.of(Role.TEMPLATE_LABEL_NAME, "true"));
        return role;
    }

    private static User user(String password, String totpSecret) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(NAME);
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setPassword(password);
        spec.setTotpEncryptedSecret(totpSecret);
        user.setSpec(spec);
        return user;
    }

    @Test
    void createRoleWithoutDependenciesPasses() {
        StepVerifier.create(policy.checkCreate(roleGvk(), role("custom-role", null)))
                .verifyComplete();
    }

    @Test
    void createRoleRejectsMissingTemplateDependencies() {
        when(client.fetch(Role.class, "role-template-missing")).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkCreate(
                        roleGvk(), role("custom-role", "[ \"role-template-missing\" ]")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("不存在");
                })
                .verify();
    }

    @Test
    void createRoleRejectsDependenciesThatAreNotTemplates() {
        when(client.fetch(Role.class, "another-role")).thenReturn(Mono.just(role("another-role", null)));

        StepVerifier.create(policy.checkCreate(
                        roleGvk(), role("custom-role", "[ \"another-role\" ]")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("不是角色模板");
                })
                .verify();
    }

    @Test
    void createRoleAllowsTemplateDependencies() {
        when(client.fetch(Role.class, "role-template-view-users"))
                .thenReturn(Mono.just(template("role-template-view-users")));

        StepVerifier.create(policy.checkCreate(
                        roleGvk(), role("custom-role", "[ \"role-template-view-users\" ]")))
                .verifyComplete();
    }

    @Test
    void updateUserRejectsPasswordChangesThroughTheGenericRoute() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", null)));

        var requested = user("attacker-chosen", null);

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, requested))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("spec.password");
                })
                .verify();
    }

    @Test
    void updateUserRejectsClearingThePassword() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", null)));

        var requested = user(null, null);

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, requested))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void updateUserRejectsTotpSecretChanges() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", "totp-current")));

        var requested = user("encoded-current", "totp-attacker");

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, requested))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("totpEncryptedSecret"))
                .verify();
    }

    @Test
    void updateUserAllowsUnrelatedChanges() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", "totp-current")));

        var requested = user("encoded-current", "totp-current");
        requested.getSpec().setBio("updated bio");
        requested.getSpec().setPhone("13800000000");

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, requested)).verifyComplete();
    }

    @Test
    void unstructuredUpdateIsConvertedAndStillGuarded() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", null)));

        var unstructured = new cn.tolim.pudans.contract.extension.Unstructured(Map.of(
                "apiVersion", User.GROUP + "/" + User.VERSION,
                "kind", User.KIND,
                "metadata", Map.of("name", NAME),
                "spec", Map.of("password", "attacker-chosen")));

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, unstructured))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void patchHandlerPathUsesTheSameGuardThroughCheckUpdate() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", null)));

        var patched = user("replaced-through-patch", null);

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, patched))
                .expectError(AccessDeniedException.class)
                .verify();

        verifyClientLookup();
    }

    private void verifyClientLookup() {
        org.mockito.Mockito.verify(client).get(User.class, NAME);
    }
}
