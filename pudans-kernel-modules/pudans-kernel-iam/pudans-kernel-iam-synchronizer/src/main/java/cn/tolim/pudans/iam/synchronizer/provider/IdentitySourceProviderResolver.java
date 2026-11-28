package cn.tolim.pudans.iam.synchronizer.provider;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.exception.IdentitySourceProviderNotSupportedException;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderConfigUtils;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderDefinition;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderMetadata;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderRegistry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class IdentitySourceProviderResolver {

    private final IdentitySourceProviderRegistry identitySourceProviderRegistry;
    private final IdentitySourceProviderCodeReader identitySourceProviderCodeReader;
    // Persisted provider codes keyed by identity source ID, empty values are cached too
    private final Map<String, Optional<String>> providerCodes = new ConcurrentHashMap<>();
    // Resolved provider metadata keyed by identity source ID
    private final Map<String, ResolvedMetadata> metadata = new ConcurrentHashMap<>();

    public IdentitySourceProviderResolver(
            IdentitySourceProviderRegistry identitySourceProviderRegistry,
            IdentitySourceProviderCodeReader identitySourceProviderCodeReader) {
        this.identitySourceProviderRegistry = identitySourceProviderRegistry;
        this.identitySourceProviderCodeReader = identitySourceProviderCodeReader;
    }

    // Resolve the persisted provider code of an identity source
    public String resolveProviderCode(IdentitySourceEntity identitySource) {
        if (identitySource.getProvider() != null) {
            return identitySource.getProvider().getCode();
        }
        String providerCode = providerCodes.computeIfAbsent(identitySource.getId(),
                id -> Optional.ofNullable(identitySourceProviderCodeReader.findProviderCode(id)))
                .orElse(null);
        if (StringUtils.isBlank(providerCode)) {
            throw new IdentitySourceProviderNotSupportedException();
        }
        return providerCode;
    }

    // Resolve provider metadata, parsing the configuration only when the provider needs it
    public IdentitySourceProviderMetadata resolve(IdentitySourceEntity identitySource) {
        String providerCode = resolveProviderCode(identitySource);
        String basicConfig = identitySource.getBasicConfig();
        ResolvedMetadata cached = metadata.get(identitySource.getId());
        if (cached != null && cached.matches(providerCode, basicConfig)) {
            return cached.metadata();
        }
        IdentitySourceProviderDefinition definition =
                identitySourceProviderRegistry.getRequired(providerCode);
        IdentitySourceConfig config = definition.isConfigRequired()
                ? IdentitySourceProviderConfigUtils.readConfig(definition, basicConfig) : null;
        IdentitySourceProviderMetadata resolved = new IdentitySourceProviderMetadata(
                definition.getCode(), definition.getName(), definition.getRootExternalId(config),
                definition.getDataOriginType(), definition.getDataOriginName());
        metadata.put(identitySource.getId(), new ResolvedMetadata(providerCode, basicConfig, resolved));
        return resolved;
    }

    // Cached metadata together with the inputs it was derived from
    private record ResolvedMetadata(String providerCode, String basicConfig,
                                    IdentitySourceProviderMetadata metadata) {
        // Whether the cached inputs still match the identity source
        private boolean matches(String code, String config) {
            return Objects.equals(providerCode, code) && Objects.equals(basicConfig, config);
        }
    }
}