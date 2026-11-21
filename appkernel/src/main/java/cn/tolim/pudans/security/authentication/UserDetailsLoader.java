package cn.tolim.pudans.security.authentication;

import static cn.tolim.pudans.security.authorization.AuthorityUtils.ANONYMOUS_ROLE_NAME;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.AUTHENTICATED_ROLE_NAME;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static java.util.Objects.requireNonNullElse;

import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.infra.exception.UserNotFoundException;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorUtils;
import java.util.function.BooleanSupplier;
import lombok.Setter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

public class UserDetailsLoader implements ReactiveUserDetailsService, ReactiveUserDetailsPasswordService {

    private final Accounts accounts;

    private final UserRoles userRoles;


    @Setter
    private BooleanSupplier twoFactorAuthEnabled = () -> true;

    // 注入账户查询与角色服务，作为用户详情加载的来源。
    public UserDetailsLoader(Accounts accounts, UserRoles userRoles) {
        this.accounts = accounts;
        this.userRoles = userRoles;
    }

    // 更新密码后返回保留双因素设置的新用户详情。
    @Override
    public Mono<UserDetails> updatePassword(UserDetails user, String newPassword) {
        return accounts.updatePassword(user.getUsername(), newPassword).map(ignored -> withNewPassword(user, newPassword));
    }

    // 加载账户与角色并转换为认证用户，未知用户统一报凭据错误。
    @Override
    public Mono<UserDetails> findByUsername(String username) {
        var getUser = accounts.getUser(username);
        return getUser.switchIfEmpty(Mono.error(() -> new UserNotFoundException(username)))
                .onErrorMap(UserNotFoundException.class, ignored -> new BadCredentialsException("用户名或密码错误。"))
                .flatMap(user -> {
                    var name = user.getMetadata().getName();
                    var disabled = requireNonNullElse(user.getSpec().getDisabled(), false);
                    var setAuthorities = userRoles
                            .list(name)

                            .concatWithValues(AUTHENTICATED_ROLE_NAME, ANONYMOUS_ROLE_NAME)
                            .map(roleName -> new SimpleGrantedAuthority(ROLE_PREFIX + roleName))
                            .distinct()
                            .collectList();

                    return setAuthorities.flatMap(authorities -> Mono.fromSupplier(() -> {
                        var principal = new PudansUserDetails(
                                name, user.getSpec().getPassword(), authorities, disabled);
                        var twoFactorAuthSettings = TwoFactorUtils.getTwoFactorAuthSettings(user);
                        return new PudansUser.Builder(principal)
                                .twoFactorAuthEnabled(
                                        twoFactorAuthEnabled.getAsBoolean() && twoFactorAuthSettings.isAvailable())
                                .totpEncryptedSecret(user.getSpec().getTotpEncryptedSecret())
                                .build();
                    }));
                });
    }

    // 用新密码重建用户详情，并保留原有双因素认证信息。
    private UserDetails withNewPassword(UserDetails userDetails, String newPassword) {
        var updated = User.withUserDetails(userDetails).password(newPassword).build();
        if (userDetails instanceof PudansUserDetailsContract contract) {
            return new PudansUser(updated, contract.isTwoFactorAuthEnabled(), contract.getTotpEncryptedSecret());
        }
        return updated;
    }
}
