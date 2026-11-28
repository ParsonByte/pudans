package cn.tolim.pudans.iam.identitysource.scim;

import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.scim.client.ScimHttpClient;
import cn.tolim.pudans.iam.infra.validation.ValidationUtils;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.LOGGER_NAME;


@Slf4j(topic = LOGGER_NAME)
public class ScimConfigValidator implements IdentitySourceConfigValidator<ScimConfig> {

    private final Function<ScimConfig, ScimHttpClient> clientFactory;

    public ScimConfigValidator() {
        this(ScimHttpClient::new);
    }

    public ScimConfigValidator(Function<ScimConfig, ScimHttpClient> clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public Boolean validate(ScimConfig config) {
        try {
            ValidationUtils.ValidationResult<ScimConfig> validationResult =
                    ValidationUtils.validateEntity(config);
            if (validationResult.isHasErrors()) {
                log.error("SCIM configuration validation failed: {}", validationResult.getMessage());
                throw new ConstraintViolationException(validationResult.getConstraintViolations());
            }
            ScimHttpClient client = clientFactory.apply(config);
            try {
                client.getServiceProviderConfig();
            } catch (ApiCallException exception) {
                // Some SCIM servers do not expose the configuration endpoint
                log.warn("SCIM service provider configuration is not available: {}",
                        exception.getMessage());
            }
            client.listUsers(1, 1);
            return true;
        } catch (Exception exception) {
            log.error("SCIM identity source configuration validation failed [MESSAGE: {}]",
                    exception.getMessage());
            throw new ApiCallException(exception.getMessage(), exception);
        }
    }
}