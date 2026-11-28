package cn.tolim.pudans.iam.identitysource.core.provider;

import cn.tolim.pudans.iam.communal.exception.IdentitySourceProviderNotSupportedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Component
public class IdentitySourceProviderRegistry {

    // Registered provider definitions keyed by provider code
    private final Map<String, IdentitySourceProviderDefinition> providers;

    public IdentitySourceProviderRegistry(List<IdentitySourceProviderDefinition> definitions) {
        Map<String, IdentitySourceProviderDefinition> registered = new LinkedHashMap<>();
        if (definitions != null) {
            for (IdentitySourceProviderDefinition definition : definitions) {
                String code = definition.getCode();
                if (StringUtils.isBlank(code)) {
                    throw new IllegalStateException("Identity source provider code cannot be blank");
                }
                if (registered.containsKey(code)) {
                    throw new IllegalStateException(
                            "Duplicate identity source provider code: " + code);
                }
                registered.put(code, definition);
            }
        }
        this.providers = Collections.unmodifiableMap(registered);
    }

    // Find a provider definition by code
    public Optional<IdentitySourceProviderDefinition> find(String code) {
        if (StringUtils.isBlank(code)) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(code));
    }

    // Get a provider definition by code or fail with the identity source provider exception
    public IdentitySourceProviderDefinition getRequired(String code) {
        return find(code).orElseThrow(IdentitySourceProviderNotSupportedException::new);
    }

    // Whether the provider code is registered
    public boolean supports(String code) {
        return find(code).isPresent();
    }

    // All registered provider definitions
    public Collection<IdentitySourceProviderDefinition> getProviders() {
        return providers.values();
    }
}