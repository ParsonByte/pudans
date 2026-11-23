package cn.tolim.pudans.security.authentication.login;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LoginReactiveAuthenticationManagerTest {

    @Mock
    ReactiveUserDetailsService userDetailsService;

    @Mock
    Accounts accounts;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    ReactiveUserDetailsPasswordService passwordService;

    @Test
    void resolvesACustomLoginNameToTheResourceName() {
        var user = user("alice");
        var details = org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("encoded")
                .authorities("ROLE_USER")
                .build();
        when(accounts.findUserByLoginName("alice.login")).thenReturn(Mono.just(user));
        when(userDetailsService.findByUsername("alice")).thenReturn(Mono.just(details));
        var manager = new LoginReactiveAuthenticationManager(
                userDetailsService, accounts, passwordEncoder, passwordService);

        StepVerifier.create(manager.lookupByUsername("alice.login"))
                .expectNext(details)
                .verifyComplete();
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
