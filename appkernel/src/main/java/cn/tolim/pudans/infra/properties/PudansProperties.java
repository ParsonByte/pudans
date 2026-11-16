package cn.tolim.pudans.infra.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;


@Data
@ConfigurationProperties(prefix = "pudans")
@Validated
public class PudansProperties implements Validator {

    @Valid
    @NestedConfigurationProperty
    private final ExtensionProperties extension = new ExtensionProperties();
    @Valid
    @NestedConfigurationProperty
    private final SecurityProperties security = new SecurityProperties();
    @Valid
    @NestedConfigurationProperty
    private final WebhookProperties webhook = new WebhookProperties();
    @NotNull
    private Path workDir;
    private URL externalUrl;
    private boolean useAbsolutePermalink;
    private Set<String> initialExtensionLocations = new HashSet<>();
    private boolean requiredExtensionDisabled;

    @Override
    public boolean supports(Class<?> clazz) {
        return PudansProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        var props = (PudansProperties) target;
        var externalUrl = props.getExternalUrl();
        if (props.isUseAbsolutePermalink() && externalUrl == null) {
            errors.rejectValue(
                    "externalUrl",
                    "external-url.required.when-using-absolute-permalink",
                    "External URL is required when property `use-absolute-permalink` is set to true.");
        }

        if (externalUrl != null && !isValidExternalUrl(externalUrl)) {
            errors.rejectValue(
                    "externalUrl", "external-url.invalid-format", "External URL must be a http or https URL.");
        }
    }

    private boolean isValidExternalUrl(URL externalUrl) {
        try {
            var uri = externalUrl.toURI();
            return !uri.isOpaque()
                    && uri.getAuthority() != null
                    && Set.of("http", "https").contains(uri.getScheme());
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
