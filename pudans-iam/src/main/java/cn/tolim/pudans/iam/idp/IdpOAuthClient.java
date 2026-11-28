package cn.tolim.pudans.iam.idp;

import cn.tolim.pudans.iam.communal.entity.authn.IdentityProviderEntity;

// OAuth operations for DingTalk and Feishu identity providers
public interface IdpOAuthClient {

    // Builds the provider authorization URL for a login state
    String authorizeUrl(IdentityProviderEntity provider, String state, String redirectUri);

    // Exchanges an authorization code for the provider user profile
    IdpUserProfile exchange(IdentityProviderEntity provider, String code, String redirectUri);
}