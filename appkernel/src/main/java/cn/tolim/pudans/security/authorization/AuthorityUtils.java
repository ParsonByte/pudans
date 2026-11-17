package cn.tolim.pudans.security.authorization;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;


public enum AuthorityUtils {
    ;

    public static final String SCOPE_PREFIX = "SCOPE_";

    public static final String ROLE_PREFIX = "ROLE_";

    public static final String SUPER_ROLE_NAME = "super-role";

    public static final String AUTHENTICATED_ROLE_NAME = "authenticated";

    public static final String ANONYMOUS_ROLE_NAME = "anonymous";


    public static Set<String> authoritiesToRoles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> StringUtils.startsWith(authority, ROLE_PREFIX))
                .map(authority -> {
                    authority = StringUtils.removeStart(authority, ROLE_PREFIX);
                    return authority;
                })
                .collect(Collectors.toSet());
    }

    public static Set<String> authoritiesToScopes(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> StringUtils.startsWith(authority, SCOPE_PREFIX))
                .map(authority -> StringUtils.removeStart(authority, SCOPE_PREFIX))
                .collect(Collectors.toSet());
    }

    public static boolean containsSuperRole(Collection<String> roles) {
        return roles.contains(SUPER_ROLE_NAME);
    }
}
