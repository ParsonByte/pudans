package cn.tolim.pudans.iam.authentication.core.authentication;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderAuthenticationService;
import cn.tolim.pudans.iam.authentication.core.exception.IdentityProviderUserExistBindException;
import cn.tolim.pudans.iam.authentication.core.exception.UserBindIdentityProviderException;
import cn.tolim.pudans.iam.infra.context.ServletContextService;
import cn.tolim.pudans.iam.infra.enums.SecretType;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import cn.tolim.pudans.iam.infra.util.AesUtils;
import cn.tolim.pudans.iam.infra.validation.ValidationUtils;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.Assert;

import java.io.Serial;
import java.io.Serializable;


public class IdentityProviderAuthenticationProvider implements AuthenticationProvider {

    private final Logger logger = LoggerFactory
            .getLogger(IdentityProviderAuthenticationProvider.class);
    private final UserDetailsService userDetailsService;
    private final IdentityProviderAuthenticationService identityProviderAuthenticationService;
    private final PasswordEncoder passwordEncoder;

    public IdentityProviderAuthenticationProvider(UserDetailsService userDetailsService,
                                                  IdentityProviderAuthenticationService identityProviderAuthenticationService,
                                                  PasswordEncoder passwordEncoder) {
        Assert.notNull(userDetailsService, "userDetailsService 不能为空");
        Assert.notNull(passwordEncoder, "passwordEncoder 不能为空");
        Assert.notNull(identityProviderAuthenticationService, "userIdpService 不能为空");
        this.identityProviderAuthenticationService = identityProviderAuthenticationService;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    // Performs authentication with the same contract as
    // AuthenticationManager#authenticate(Authentication)
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        IdentityProviderBindAuthentication identityProviderBindAuthentication = (IdentityProviderBindAuthentication) authentication;
        String username = identityProviderBindAuthentication.getUsername();
        String password = identityProviderBindAuthentication.getPassword();
        ValidationUtils.ValidationResult<UsernamePasswordBindIdpRequest> requestValidationResult = ValidationUtils
                .validateEntity(new UsernamePasswordBindIdpRequest(username, password));
        if (requestValidationResult.isHasErrors()) {
            throw new ConstraintViolationException(
                    requestValidationResult.getConstraintViolations());
        }
        // Retrieve and decrypt the secret
        try {
            String secret = (String) ServletContextService.getSession()
                    .getAttribute(SecretType.LOGIN.getKey());
            password = AesUtils.decrypt(password, secret);
        } catch (Exception exception) {
            String content = "用户 [" + username + "] 绑定 IDP 失败, 密码解密异常";
            logger.error(content, exception);
            throw new UserBindIdentityProviderException();
        }
        // Run binding logic
        IdentityProviderNotBindAuthentication identityProviderNotBindAuthentication = (IdentityProviderNotBindAuthentication) SecurityUtils
                .getSecurityContext().getAuthentication();
        IdentityProviderUserDetails identityProviderUserDetails = (IdentityProviderUserDetails) identityProviderNotBindAuthentication
                .getPrincipal();
        try {
            UserDetails userDetails = retrieveUser(username);
            if (!this.passwordEncoder.matches(password, userDetails.getPassword())) {
                logger.debug("密码与存储值不匹配，认证失败");
                throw new BadCredentialsException("用户名或密码错误");
            }
            // Bind
            Boolean isExistBind = identityProviderAuthenticationService.checkIdpUserIsExistBind(
                    identityProviderUserDetails.getOpenId(),
                    identityProviderUserDetails.getProviderId());
            if (isExistBind) {
                logger.error("身份提供商【{}】用户【{}】已存在系统用户绑定",
                        identityProviderUserDetails.getProviderId(),
                        identityProviderUserDetails.getOpenId());
                throw new IdentityProviderUserExistBindException();
            }
            if (identityProviderAuthenticationService.bindUserIdp(userDetails.getId(),
                    identityProviderUserDetails)) {
                // Build IdpAuthentication
                return new IdentityProviderAuthentication(userDetails,
                        identityProviderUserDetails.getProviderType(),
                        identityProviderUserDetails.getProviderId(), userDetails.getAuthorities());
            }
            throw new UserBindIdentityProviderException();
        } catch (UsernameNotFoundException ex) {
            logger.debug("未找到用户 '{}'", username);
            throw new BadCredentialsException("用户名或密码错误");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return IdentityProviderBindAuthentication.class.isAssignableFrom(authentication);
    }

    protected final UserDetails retrieveUser(String username) throws AuthenticationException {
        try {
            UserDetails loadedUser = (UserDetails) this.userDetailsService
                    .loadUserByUsername(username);
            if (loadedUser == null) {
                throw new InternalAuthenticationServiceException(
                        "UserDetailsService 返回 null，违反接口契约");
            }
            return loadedUser;
        } catch (UsernameNotFoundException | InternalAuthenticationServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InternalAuthenticationServiceException(ex.getMessage(), ex);
        }
    }

    @Data
    public static final class UsernamePasswordBindIdpRequest implements Serializable {

        @Serial
        private static final long serialVersionUID = -6222816278396139727L;
        @NotBlank(message = "用户名不能为空")
        private final String username;
        @NotBlank(message = "密码不能为空")
        private final String password;

        public UsernamePasswordBindIdpRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
