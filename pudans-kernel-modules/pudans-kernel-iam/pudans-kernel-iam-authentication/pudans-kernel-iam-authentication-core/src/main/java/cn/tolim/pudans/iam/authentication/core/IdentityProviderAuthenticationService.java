package cn.tolim.pudans.iam.authentication.core;

import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderUserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;


public interface IdentityProviderAuthenticationService {

    // Check whether the user is bound to the platform
    Boolean checkIdpUserIsExistBind(String openId, String providerId);

    // Bind
    Boolean bindUserIdp(String accountId, IdentityProviderUserDetails identityProviderUserDetails);

    // Update account information
    Boolean updateThirdPartyUser(IdentityProviderUserDetails identityProviderUserDetails,
                                 String providerId);

    // Get user details
    UserDetails getUserDetails(String openId, String providerId);
}
