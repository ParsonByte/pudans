package cn.tolim.pudans.iam.identitysource.core.provider;

import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;


public interface IdentitySourceProviderDefinition {

    // Provider code stored in the identity source record
    String getCode();

    // Provider display name
    String getName();

    // Provider configuration type
    Class<? extends IdentitySourceConfig> getConfigType();

    // Provider configuration validator
    IdentitySourceConfigValidator<? extends IdentitySourceConfig> getConfigValidator();

    // Create the provider client for a configuration instance
    IdentitySourceClient createClient(IdentitySourceConfig config);

    // Identity source implementation type
    Class<? extends IdentitySource<? extends IdentitySourceConfig>> getIdentitySourceType();

    // External ID of the synthetic root department, providers without a root department return their own constant
    String getRootExternalId(IdentitySourceConfig config);

    // Data origin type recorded on synchronized users and organizations
    String getDataOriginType();

    // Data origin display name recorded on synchronized users and organizations
    String getDataOriginName();

    // Whether the metadata methods need the configuration, providers with constant root IDs do not
    default boolean isConfigRequired() {
        return true;
    }
}