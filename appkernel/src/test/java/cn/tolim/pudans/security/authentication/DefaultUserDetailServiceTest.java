package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DefaultUserDetailServiceTest {

    @Mock
    UserService userService;

    @Mock
    UserGroupService userGroupService;

    @Test
    void passwordUpgradeKeepsTwoFactorMetadata() {
        var principal = new PudansUserDetails(
                "alice", "old", List.of(new SimpleGrantedAuthority("ROLE_USER")), false);
        var user = new PudansUser(principal, true, "totp-secret");
        when(userService.updatePassword("alice", "new")).thenReturn(Mono.just(new User()));
        var service = new DefaultUserDetailService(userService, userGroupService);

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
