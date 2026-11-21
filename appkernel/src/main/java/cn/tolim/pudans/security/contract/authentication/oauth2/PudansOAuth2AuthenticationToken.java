package cn.tolim.pudans.security.contract.authentication.oauth2;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;


public class PudansOAuth2AuthenticationToken extends AbstractAuthenticationToken implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    private final UserDetails userDetails;

    @Getter
    private final OAuth2AuthenticationToken original;


    // 合并外部账号与本地权限并标记已认证
    public PudansOAuth2AuthenticationToken(UserDetails userDetails, OAuth2AuthenticationToken original) {
        super(combineAuthorities(userDetails, original));
        this.userDetails = userDetails;
        this.original = original;
        setAuthenticated(true);
    }

    // 以工厂方法表达创建已认证令牌
    public static PudansOAuth2AuthenticationToken authenticated(
            UserDetails userDetails, OAuth2AuthenticationToken original) {
        return new PudansOAuth2AuthenticationToken(userDetails, original);
    }

    // 合并两类权限并保持不可变列表
    private static Collection<? extends GrantedAuthority> combineAuthorities(
            UserDetails userDetails, OAuth2AuthenticationToken original) {
        var userDetailsAuthorities = userDetails.getAuthorities();
        var originalAuthorities = original.getAuthorities();
        var authorities = new ArrayList<GrantedAuthority>(originalAuthorities.size() + userDetailsAuthorities.size());
        authorities.addAll(originalAuthorities);
        authorities.addAll(userDetailsAuthorities);
        return Collections.unmodifiableList(authorities);
    }

    // 对外身份使用本地用户名而非外部主体
    @Override
    public String getName() {
        return userDetails.getUsername();
    }

    // 合并权限，保证构造与运行时一致
    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        var originalAuthorities = super.getAuthorities();
        var userDetailsAuthorities = getUserDetails().getAuthorities();
        var authorities = new ArrayList<GrantedAuthority>(originalAuthorities.size() + userDetailsAuthorities.size());
        authorities.addAll(originalAuthorities);
        authorities.addAll(userDetailsAuthorities);
        return Collections.unmodifiableList(authorities);
    }

    // OAuth2 令牌不暴露凭据
    @Override
    public Object getCredentials() {
        return "";
    }

    // 保留原始 OAuth2 用户作为主体
    @Override
    public OAuth2User getPrincipal() {
        return original.getPrincipal();
    }
}
