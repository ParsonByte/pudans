package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthenticationContext;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.util.Assert;

import java.util.Map;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext implements
        OAuth2AuthenticationContext {
    private final Map<Object, Object> context;

    private OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext(Map<Object, Object> context) {
        this.context = Map.copyOf(context);
    }

    // Constructs a new Builder with the provided OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken.
    // Parameter authentication the OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken
    // Returns the Builder
    public static Builder with(OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken authentication) {
        return new Builder(authentication);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <V> V get(Object key) {
        return hasKey(key) ? (V) this.context.get(key) : null;
    }

    @Override
    public boolean hasKey(Object key) {
        Assert.notNull(key, "key 不能为空");
        return this.context.containsKey(key);
    }

    // Returns the registered client.
    // Returns the RegisteredClient
    public RegisteredClient getRegisteredClient() {
        return get(RegisteredClient.class);
    }

    // A builder for OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext.
    public static final class Builder extends
            AbstractBuilder<OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext, Builder> {

        private Builder(OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken authentication) {
            super(authentication);
        }

        // Sets the registered client.
        // Parameter registeredClient the RegisteredClient
        // Returns the Builder for further configuration
        public Builder registeredClient(RegisteredClient registeredClient) {
            return put(RegisteredClient.class, registeredClient);
        }

        // Builds a new OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext.
        // Returns the OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext
        @Override
        public OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext build() {
            Assert.notNull(get(RegisteredClient.class), "registeredClient 不能为空");
            return new OAuth2AuthorizationResourceOwnerPasswordAuthenticationContext(getContext());
        }

    }

}
