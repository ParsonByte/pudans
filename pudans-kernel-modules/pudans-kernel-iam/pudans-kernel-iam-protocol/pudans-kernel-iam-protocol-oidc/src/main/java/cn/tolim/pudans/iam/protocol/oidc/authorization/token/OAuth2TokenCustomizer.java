package cn.tolim.pudans.iam.protocol.oidc.authorization.token;

import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.util.BeanUtils;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.DEFAULT_DATE_TIME_FORMATTER;
import static org.springframework.security.oauth2.core.oidc.OidcScopes.*;


@Setter
@SuppressWarnings({"AlibabaClassNamingShouldBeCamel"})
public class OAuth2TokenCustomizer implements
        org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer<JwtEncodingContext> {

    private UserRepository userRepository;

    @Override
    public void customize(JwtEncodingContext context) {
        Set<String> authorizedScopes = context.getAuthorizedScopes();
        if (context.getTokenType().getValue().equals(OidcParameterNames.ID_TOKEN)) {
            OAuth2Authorization authorization = context.getAuthorization();
            if (!Objects.isNull(authorization)){
                Authentication authentication = authorization.getAttribute(Principal.class.getName());
                if (!Objects.isNull(authentication)){
                    // UserRepository provides real-time user lookup
                    if (!Objects.isNull(userRepository)){
                        UserDetails principal = (UserDetails) authentication.getPrincipal();
                        Optional<UserEntity> optional = userRepository.findById(principal.getId());
                        optional.ifPresent(user->BeanUtils.merge(user.toUserDetails(principal.getAuthorities()),principal));
                    }
                    UserDetails user = (UserDetails) authentication.getPrincipal();
                        boolean hasCustomClaims=false;
                        OidcUserInfo.Builder userInfoBuilder = OidcUserInfo.builder();
                        // Customize headers/claims for id_token
                        // User principal
                        userInfoBuilder.subject(user.getId());
                        if (authorizedScopes.contains(EMAIL)) {
                            hasCustomClaims=true;
                            userInfoBuilder.email(Objects.toString(user.getEmail(), ""));
                            userInfoBuilder.emailVerified(!Objects.isNull(user.getEmailVerified()) && user.getEmailVerified());
                        }
                        if (authorizedScopes.contains(PHONE)) {
                            hasCustomClaims=true;
                            userInfoBuilder.phoneNumber(Objects.toString(user.getPhone(), ""));
                            userInfoBuilder.phoneNumberVerified(!Objects.isNull(user.getPhoneVerified()) && user.getPhoneVerified());
                        }
                        if (authorizedScopes.contains(PROFILE)) {
                            hasCustomClaims=true;
                            // Username
                            userInfoBuilder.preferredUsername(user.getUsername());
                            // Nickname
                            userInfoBuilder.nickname(Objects.toString(user.getNickName(), ""));
                            userInfoBuilder.picture(user.getAvatar());
                            userInfoBuilder.updatedAt(user.getUpdateTime().format(DEFAULT_DATE_TIME_FORMATTER));
                        }
                        if (hasCustomClaims){
                            context.getClaims().claims(claims ->
                                    claims.putAll(userInfoBuilder.build().getClaims()));
                        }
                }
            }
        }
    }

}
