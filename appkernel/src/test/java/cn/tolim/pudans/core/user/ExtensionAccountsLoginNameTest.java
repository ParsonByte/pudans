package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ExtensionAccountsLoginNameTest {

    @Mock
    ReactiveExtensionClient client;

    private ExtensionAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new ExtensionAccounts(client, null, null, null, null, null, null, null);
    }

    @Test
    void resolvesAnExplicitLoginNameBeforeTheResourceName() {
        var user = user("alice");
        user.getSpec().setLoginName("alice.login");
        when(client.listAll(eq(User.class), any(), any())).thenReturn(Flux.just(user));

        StepVerifier.create(accounts.findUserByLoginName(" Alice.Login "))
                .expectNext(user)
                .verifyComplete();

        var options = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listAll(eq(User.class), options.capture(), any());
        assertThat(options.getValue().toCondition().toString()).contains("spec.loginName = alice.login");
    }

    @Test
    void fallsBackToTheResourceNameWhenNoCustomLoginNameExists() {
        var user = user("alice");
        when(client.listAll(eq(User.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(user));

        StepVerifier.create(accounts.findUserByLoginName("Alice"))
                .expectNext(user)
                .verifyComplete();
    }

    @Test
    void doesNotFallBackToTheResourceNameAfterACustomLoginNameIsSet() {
        var user = user("alice");
        user.getSpec().setLoginName("alice.login");
        when(client.listAll(eq(User.class), any(), any())).thenReturn(Flux.empty());
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(user));

        StepVerifier.create(accounts.findUserByLoginName("alice")).verifyComplete();
    }

    @Test
    void ignoresTheCurrentUserWhenCheckingLoginNameAvailability() {
        when(client.listAll(eq(User.class), any(), any())).thenReturn(Flux.just(user("alice")));

        StepVerifier.create(accounts.isLoginNameAvailable("alice", "alice"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void reportsALoginNameClaimedByAnotherUser() {
        when(client.listAll(eq(User.class), any(), any())).thenReturn(Flux.just(user("bob")));

        StepVerifier.create(accounts.isLoginNameAvailable("bob", "alice"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void rejectsCreationWhenTheLoginNameIsAlreadyClaimed() {
        var conflict = user("alice");
        conflict.getSpec().setLoginName("bob");
        when(client.fetch(User.class, "bob")).thenReturn(Mono.empty());
        when(client.listAll(eq(User.class), any(), any())).thenReturn(Flux.just(conflict));

        StepVerifier.create(accounts.createUser(user("bob")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DuplicateNameException.class)
                        .hasMessageContaining("登录名 bob 已被占用"))
                .verify();

        verify(client, never()).create(any(User.class));
    }

    private static User user(String name) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(name);
        user.setMetadata(metadata);
        user.setSpec(new User.UserSpec());
        return user;
    }
}
