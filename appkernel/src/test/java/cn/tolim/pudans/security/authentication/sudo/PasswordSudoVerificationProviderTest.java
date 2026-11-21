package cn.tolim.pudans.security.authentication.sudo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PasswordSudoVerificationProviderTest {

    @Mock
    Accounts accounts;

    // 只有设置了密码的账户才提供密码确认。
    @Test
    void supportsOnlyAccountsWithPassword() {
        var provider = new PasswordSudoVerificationProvider(accounts);
        var withPassword = user("encoded");
        var withoutPassword = user(null);

        StepVerifier.create(provider.supports(withPassword))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(provider.supports(withoutPassword))
                .expectNext(false)
                .verifyComplete();
    }

    // 当前密码正确时确认成功。
    @Test
    void confirmsWithTheCurrentPassword() {
        var provider = new PasswordSudoVerificationProvider(accounts);
        var user = user("encoded");
        when(accounts.confirmPassword("alice", "secret")).thenReturn(Mono.just(true));

        StepVerifier.create(provider.verify(user, "secret")).verifyComplete();
    }

    // 密码错误时统一返回确认失败。
    @Test
    void rejectsWrongPassword() {
        var provider = new PasswordSudoVerificationProvider(accounts);
        var user = user("encoded");
        when(accounts.confirmPassword("alice", "wrong")).thenReturn(Mono.just(false));

        StepVerifier.create(provider.verify(user, "wrong"))
                .expectError(SudoVerificationFailedException.class)
                .verify();
    }

    private static User user(String password) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName("alice");
        user.setMetadata(metadata);
        user.getSpec().setPassword(password);
        return user;
    }
}
