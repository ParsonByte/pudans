package cn.tolim.pudans.iam.authentication.core.filter;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderAuthenticationService;
import cn.tolim.pudans.iam.authentication.core.IdentityProviderType;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderAuthentication;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderNotBindAuthentication;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderUserDetails;
import cn.tolim.pudans.iam.authentication.core.client.IdentityProviderConfig;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClient;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClientRepository;
import cn.tolim.pudans.iam.authentication.core.exception.UserBindIdentityProviderException;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.infra.context.ApplicationContextService;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.Optional;

import static cn.tolim.pudans.iam.authentication.core.constant.AuthenticationConstants.*;


@Getter
public abstract class AbstractIdentityProviderAuthenticationProcessingFilter extends
        AbstractAuthenticationProcessingFilter {
    public static final String INVALID_IDP = "invalid_idp";
    public static final String GET_USERINFO_ERROR_CODE = "get_userinfo_error_code";
    public static final String INVALID_IDP_CONFIG = "invalid_idp_config";
    // Authorization request repository
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository;
    // Authenticated user details
    private final IdentityProviderAuthenticationService identityProviderAuthenticationService;
    // Identity provider repository
    private final RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository;

    // Creates a new instance
    protected AbstractIdentityProviderAuthenticationProcessingFilter(RequestMatcher requiresAuthenticationRequestMatcher,
                                                                     IdentityProviderAuthenticationService identityProviderAuthenticationService,
                                                                     RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository) {
        super(requiresAuthenticationRequestMatcher);
        this.identityProviderAuthenticationService = identityProviderAuthenticationService;
        this.registeredIdentityProviderClientRepository = registeredIdentityProviderClientRepository;
        this.authorizationRequestRepository = new HttpSessionOAuth2AuthorizationRequestRepository();
    }

    // User authentication
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response,
                                                IdentityProviderUserDetails identityProviderUserDetails) throws IOException {

        String providerId = identityProviderUserDetails.getProviderId();
        HttpSession session = request.getSession(false);
        // Bind after login
        if (SecurityUtils.isAuthenticated()) {
            UserDetails currentUser = SecurityUtils.getCurrentUser();
            logger.debug("用户【" + currentUser.getUsername() + "】绑定【"
                    + identityProviderUserDetails.getOpenId() + "】");
            // Bind the current provider user
            identityProviderAuthenticationService.bindUserIdp(currentUser.getId(),
                    identityProviderUserDetails);
            session.setAttribute(BIND_AFTER_AUTH, true);
            session.setAttribute(BIND_REDIRECT, Boolean.TRUE);
            return SecurityUtils.getSecurityContext().getAuthentication();
        }

        // Unbound identity source
        if (!identityProviderAuthenticationService
                .checkIdpUserIsExistBind(identityProviderUserDetails.getOpenId(), providerId)) {
            logger.debug(
                    "【" + providerId + "】用户【" + identityProviderUserDetails.getOpenId() + "】未绑定");
            // Auto-bind
            UserRepository userRepository = ApplicationContextService.getBean(UserRepository.class);
            Optional<UserEntity> userOptional = userRepository
                    .findByExternalId(identityProviderUserDetails.getOpenId());
            if (userOptional.isPresent()) {
                try {
                    identityProviderAuthenticationService.bindUserIdp(
                            String.valueOf(userOptional.get().getId()), identityProviderUserDetails);
                } catch (UserBindIdentityProviderException e) {
                    logger.error("【" + providerId + "】用户【" + identityProviderUserDetails.getOpenId()
                                    + "】自动绑定失败",
                            e);
                    return getIdpNotBindAuthentication(request, identityProviderUserDetails);
                }
            } else {
                return getIdpNotBindAuthentication(request, identityProviderUserDetails);
            }
        }

        logger.debug("【" + providerId + "】用户【" + identityProviderUserDetails.getOpenId() + "】已绑定");
        // Update account information when a binding exists
        if (!identityProviderAuthenticationService.updateThirdPartyUser(identityProviderUserDetails,
                providerId)) {
            logger.error(
                    "【" + providerId + "】用户【" + identityProviderUserDetails.getOpenId() + "】更新信息失败");
        }
        session.setAttribute(BIND_REDIRECT, Boolean.TRUE);
        return authenticate(identityProviderUserDetails.getOpenId(), providerId,
                identityProviderUserDetails.getProviderType(), request);
    }

    // Unbound identity source response
    @NotNull
    private IdentityProviderNotBindAuthentication getIdpNotBindAuthentication(HttpServletRequest request,
                                                                              IdentityProviderUserDetails identityProviderUserDetails) {
        IdentityProviderNotBindAuthentication token = new IdentityProviderNotBindAuthentication(
                identityProviderUserDetails);
        token.setDetails(this.authenticationDetailsSource.buildDetails(request));
        return token;
    }

    // Authentication
    public Authentication authenticate(String openId, String providerId,
                                       IdentityProviderType providerType,
                                       HttpServletRequest request) {
        // Authentication
        UserDetails userDetails = identityProviderAuthenticationService.getUserDetails(openId,
                providerId);
        IdentityProviderAuthentication token = new IdentityProviderAuthentication(userDetails,
                providerType, providerId, userDetails.getAuthorities());
        // Allow subclasses to set the "details" property
        token.setDetails(this.authenticationDetailsSource.buildDetails(request));
        return token;
    }

    public <T extends IdentityProviderConfig> RegisteredIdentityProviderClient<T> getRegisteredIdentityProviderClient(String code) {
        Optional<RegisteredIdentityProviderClient<T>> optional = registeredIdentityProviderClientRepository
                .findByCode(code);
        if (optional.isEmpty()) {
            // Invalid identity provider
            OAuth2Error oauth2Error = new OAuth2Error(INVALID_IDP);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        return optional.get();
    }

    public <T extends IdentityProviderConfig> String getIdentityProviderId(String providerCode) {
        RegisteredIdentityProviderClient<T> identityProvider = getRegisteredIdentityProviderClient(
                providerCode);
        return String.valueOf(identityProvider.getId());
    }

    public OAuth2AuthorizationRequest getOauth2AuthorizationRequest(HttpServletRequest request,
                                                                    HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequestRepository
                .removeAuthorizationRequest(request, response);
        if (authorizationRequest == null) {
            OAuth2Error oauth2Error = new OAuth2Error(AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        return authorizationRequest;
    }
}
