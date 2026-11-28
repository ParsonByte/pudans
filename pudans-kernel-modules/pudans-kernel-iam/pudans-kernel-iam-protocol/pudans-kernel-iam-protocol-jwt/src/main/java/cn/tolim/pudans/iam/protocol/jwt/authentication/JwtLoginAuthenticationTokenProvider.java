
package cn.tolim.pudans.iam.protocol.jwt.authentication;

import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import cn.tolim.pudans.iam.communal.exception.app.AppAccountNotExistException;
import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.infra.security.authentication.WebAuthenticationDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.protocol.core.LoginAccount;
import cn.tolim.pudans.iam.protocol.jwt.JwtAuthentication;
import cn.tolim.pudans.iam.protocol.jwt.JwtAuthorizationService;
import cn.tolim.pudans.iam.protocol.jwt.client.JwtRegisteredClient;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtAuthenticationException;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtError;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtErrorCodes;
import cn.tolim.pudans.iam.protocol.jwt.token.IdToken;
import cn.tolim.pudans.iam.protocol.jwt.token.IdTokenContext;
import cn.tolim.pudans.iam.protocol.jwt.token.IdTokenGenerator;
import cn.tolim.pudans.iam.protocol.jwt.token.JwtIdTokenGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.time.Duration;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE_VARIABLE;
import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.JwtEndpointConstants.JWT_SSO_PATH;
import static cn.tolim.pudans.iam.infra.security.util.SecurityUtils.isPrincipalAuthenticated;
import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.JWT_ERROR_URI;


public final class JwtLoginAuthenticationTokenProvider implements AuthenticationProvider {

    private final Log logger = LogFactory.getLog(JwtLoginAuthenticationTokenProvider.class);
    private final IdTokenGenerator tokenGenerator = new JwtIdTokenGenerator();
    private final JwtAuthorizationService authorizationService;


    public JwtLoginAuthenticationTokenProvider(JwtAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            JwtLoginAuthenticationToken requestAuthenticationToken = (JwtLoginAuthenticationToken) authentication;
            Authentication principal = (Authentication) requestAuthenticationToken.getPrincipal();
            if (!isPrincipalAuthenticated(principal)) {
                this.logger.info("主体未认证，未对 JWT 请求执行认证");
                return authentication;
            }
            JwtProtocolConfig config = requestAuthenticationToken.getConfig();
            String issuer = ContextService.getPortalPublicBaseUrl() + JWT_SSO_PATH.replace(APP_CODE_VARIABLE, config.getAppCode());
            String subject = getSubject(config,(UserDetails) principal.getPrincipal());
            WebAuthenticationDetails details = (WebAuthenticationDetails) requestAuthenticationToken.getDetails();
            IdTokenContext tokenContext = IdTokenContext.builder()
                    .issuer(issuer)
                    .subject(subject)
                    .audience(config.getAppCode())
                    .sessionId(details.getSessionId())
                    .idTokenTimeToLive(Duration.ofSeconds(Long.parseLong(config.getIdTokenTimeToLive())))
                    .privateKey(config.getJwtPrivateKey())
                    .build();

            IdToken idToken = tokenGenerator.generate(tokenContext);
            JwtAuthentication jwtAuthentication= JwtAuthentication.builder()
                    .token(idToken.getTokenValue())
                    .issuer(issuer)
                    .subject(subject)
                    .audience(config.getAppCode())
                    .principal(principal)
                    .issuedAt(idToken.getIssuedAt())
                    .expiresAt(idToken.getExpiresAt())
                    .build();
            // save
            authorizationService.save(jwtAuthentication);
            this.logger.info("已保存包含 ID 令牌的授权");
            JwtAuthenticationToken jwtAuthenticationToken = new JwtAuthenticationToken(principal, requestAuthenticationToken.getConfig(), idToken);
            jwtAuthenticationToken.setTargetUrl(requestAuthenticationToken.getTargetUrl());
            return jwtAuthenticationToken;

        } catch (Exception e) {
            if (e instanceof JwtAuthenticationException){
                throw e;
            }

            if (e instanceof AppAccountNotExistException){
                JwtError error = new JwtError(JwtErrorCodes.APP_ACCOUNT_NOT_EXIST,"应用账户不存在",JWT_ERROR_URI);
                throw new JwtAuthenticationException(error);
            }

            throw new JwtAuthenticationException(new JwtError(JwtErrorCodes.SERVER_ERROR,e.getMessage(),JWT_ERROR_URI));
        }
    }

    private String getSubject(JwtProtocolConfig config, UserDetails user) {
        switch (config.getIdTokenSubjectType()) {
            case USER_ID -> {
                return user.getUsername();
            }
            case APP_USER -> {
                JwtRegisteredClient registeredClient = JwtRegisteredClient.builder()
                        .id(config.getAppId())
                        .code(config.getAppCode())
                        .clientId(config.getClientId())
                        .clientName(config.getAppName())
                        .build();
                LoginAccount account = authorizationService.getDefaultLoginAccount(registeredClient, user);
                if (Objects.isNull(account)) {
                    throw new AppAccountNotExistException();
                }
                return account.getUsername();
            }
            default -> {
                JwtError error = new JwtError(JwtErrorCodes.CONFIG_ERROR, null, JWT_ERROR_URI);
                throw new JwtAuthenticationException(error);
            }
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.isAssignableFrom(JwtLoginAuthenticationToken.class);
    }
}
