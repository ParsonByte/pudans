package cn.tolim.pudans.iam.authentication.core.client;

import java.util.Optional;


public interface RegisteredIdentityProviderClientRepository {

    // Query identity provider configuration by code
    <T extends IdentityProviderConfig> Optional<RegisteredIdentityProviderClient<T>> findByCode(String code);
}
