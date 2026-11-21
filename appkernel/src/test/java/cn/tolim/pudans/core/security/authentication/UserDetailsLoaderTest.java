package cn.tolim.pudans.core.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.core.security.authentication.login.PudansUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserDetailsLoaderTest {

    @Mock
    Accounts accounts;

    @Mock
    Groups groups;

    @Test
    void passwordUpgradeKeepsTwoFactorMetadata() {
        var principal = new PudansUserDetails(
                "alice", "old", List.of(new SimpleGrantedAuthority("ROLE_USER")), false);
        var user = new PudansUser(principal, true, "totp-secret");
        when(accounts.updatePassword("alice", "new")).thenReturn(Mono.just(new User()));
        var service = new UserDetailsLoader(accounts, groups);

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
