package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.ldap.client.DefaultLdapConnectionFactory;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapConnectionFactory;
import cn.tolim.pudans.iam.infra.validation.ValidationUtils;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.LOGGER_NAME;


@Slf4j(topic = LOGGER_NAME)
public class LdapConfigValidator implements IdentitySourceConfigValidator<LdapConfig> {

    private final LdapConnectionFactory connectionFactory;

    public LdapConfigValidator() {
        this(new DefaultLdapConnectionFactory());
    }

    public LdapConfigValidator(LdapConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Boolean validate(LdapConfig config) {
        try {
            ValidationUtils.ValidationResult<LdapConfig> validationResult =
                    ValidationUtils.validateEntity(config);
            if (validationResult.isHasErrors()) {
                log.error("LDAP configuration validation failed: {}", validationResult.getMessage());
                throw new ConstraintViolationException(validationResult.getConstraintViolations());
            }
            new LdapSearchService(config, connectionFactory).testConnection();
            return true;
        } catch (Exception exception) {
            log.error("LDAP identity source configuration validation failed [MESSAGE: {}]",
                    exception.getMessage());
            throw new ApiCallException(exception.getMessage(), exception);
        }
    }
}