package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserDetailsLoaderTest {

    @Mock
    Accounts accounts;

    @Mock
    UserRoles userRoles;

    // 登录时必须同时加载直绑角色和用户组角色。
    @Test
    void loadsDirectAndGroupRoles() {
        var account = new User();
        var metadata = new Metadata();
        metadata.setName("alice");
        account.setMetadata(metadata);
        account.setSpec(new User.UserSpec());
        when(accounts.getUser("alice")).thenReturn(Mono.just(account));
        when(userRoles.list("alice")).thenReturn(Flux.just("direct-role", "group-role"));
        var service = new UserDetailsLoader(accounts, userRoles);

        StepVerifier.create(service.findByUsername("alice"))
                .assertNext(details -> assertThat(details.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList())
                        .contains("ROLE_direct-role", "ROLE_group-role"))
                .verifyComplete();
    }

    @Test
    void passwordUpgradeKeepsTwoFactorMetadata() {
        var principal = new PudansUserDetails(
                "alice", "old", List.of(new SimpleGrantedAuthority("ROLE_USER")), false);
        var user = new PudansUser(principal, true, "totp-secret");
        when(accounts.updatePassword("alice", "new")).thenReturn(Mono.just(new User()));
        var service = new UserDetailsLoader(accounts, userRoles);

        StepVerifier.create(service.updatePassword(user, "new"))
                .assertNext(updated -> {
                    assertThat(updated).isInstanceOf(PudansUserDetailsContract.class);
                    var contract = (PudansUserDetailsContract) updated;
                    assertThat(contract.isTwoFactorAuthEnabled()).isTrue();
                    assertThat(contract.getTotpEncryptedSecret()).isEqualTo("totp-secret");
                    assertThat(updated.getPassword()).isEqualTo("new");
                })
                .verifyComplete();
    }
}
