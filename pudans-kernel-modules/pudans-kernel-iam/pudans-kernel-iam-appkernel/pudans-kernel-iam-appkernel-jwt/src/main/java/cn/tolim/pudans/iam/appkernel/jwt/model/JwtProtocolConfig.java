package cn.tolim.pudans.iam.appkernel.jwt.model;

import cn.tolim.pudans.iam.appkernel.core.AbstractProtocolConfig;
import cn.tolim.pudans.iam.communal.enums.app.JwtBindingType;
import cn.tolim.pudans.iam.communal.enums.app.JwtIdTokenSubjectType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.io.Serial;


@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class JwtProtocolConfig extends AbstractProtocolConfig {

    @Serial
    private static final long serialVersionUID = -3671812647788723766L;

    // JWT SSO URL in the business system. During single sign-on, the system sends id_token information to this address via [GET] with the parameter name id_token.
    // The business system can use id_token and the public key to obtain user information. If login starts in the business system (SP), the system validates the service parameter on the SP login address when present.
    // On success, the browser is redirected to this address with the id_token identity token.
    private String redirectUrl;

    // URL to redirect to after a successful JWT SSO in the business system, usually a secondary menu or similar.
    // If set, this URL is passed as target_uri during JWT SSO.
    // If not set, a target_uri request parameter from SSO is forwarded. Optional.
    private String targetLinkUrl;

    // SSO binding type
    private JwtBindingType bindingType;

    // Token expiration time (seconds)
    private String idTokenTimeToLive;

    // JWT public key
    private String jwtPublicKey;

    // JWT private key
    private String jwtPrivateKey;

    // id_token principal type
    private JwtIdTokenSubjectType idTokenSubjectType;

    // Logout redirect URL
    private String postLogoutRedirectUri;

    // Configured
    private Boolean configured;
}
