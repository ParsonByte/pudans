package cn.tolim.pudans.core.security.authentication;

import static cn.tolim.pudans.core.security.authorization.AuthorityUtils.ANONYMOUS_ROLE_NAME;
import static cn.tolim.pudans.core.security.authorization.AuthorityUtils.AUTHENTICATED_ROLE_NAME;
import static cn.tolim.pudans.core.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static java.util.Objects.requireNonNullElse;

import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.core.infra.exception.UserNotFoundException;
import cn.tolim.pudans.core.security.authentication.login.PudansUser;
import cn.tolim.pudans.core.security.authentication.twofactor.TwoFactorUtils;
import java.util.function.BooleanSupplier;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

@Slf4j
public class UserDetailsLoader implements ReactiveUserDetailsService, ReactiveUserDetailsPasswordService {

    private final Accounts accounts;

    private final Groups groups;


    @Setter
    private BooleanSupplier twoFactorAuthEnabled = () -> true;

    public UserDetailsLoader(Accounts accounts, Groups groups) {
        this.accounts = accounts;
        this.groups = groups;
    }

    @Override
    public Mono<UserDetails> updatePassword(UserDetails user, String newPassword) {
        return accounts.updatePassword(user.getUsername(), newPassword).map(u -> withNewPassword(user, newPassword));
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        var getUser = accounts.getUser(username);
        return getUser.switchIfEmpty(Mono.error(() -> new UserNotFoundException(username)))
                .onErrorMap(UserNotFoundException.class, ignored -> new BadCredentialsException("用户名或密码错误。"))
                .flatMap(user -> {
                    var name = user.getMetadata().getName();
                    var disabled = requireNonNullElse(user.getSpec().getDisabled(), false);
                    var setAuthorities = groups
                            .getRoleNamesByUsername(name)

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

    private UserDetails withNewPassword(UserDetails userDetails, String newPassword) {
        var updated = User.withUserDetails(userDetails).password(newPassword).build();
        if (userDetails instanceof PudansUserDetailsContract contract) {
            return new PudansUser(updated, contract.isTwoFactorAuthEnabled(), contract.getTotpEncryptedSecret());
        }
        return updated;
    }
}
