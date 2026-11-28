package cn.tolim.pudans.iam.communal.entity.app;

import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.util.Set;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_app_oidc_config")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AppOidcConfigEntity extends BaseEntity {

    // APP ID
    @Column(name = "app_id")
    private String appId;

    // Client authentication method
    @Column(name = "client_auth_methods")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<String> clientAuthMethods;

    // Grant type
    @Column(name = "auth_grant_types")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<String> authGrantTypes;

    // Response type
    @Column(name = "response_types")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<String> responseTypes;

    // Redirect URIs
    @Column(name = "redirect_uris")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<String> redirectUris;

    // Logout redirect URIs
    @Column(name = "post_logout_redirect_uris")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<String> postLogoutRedirectUris;

    // scopes
    @Column(name = "grant_scopes")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<String> grantScopes;

    // Requires authorization consent
    @Column(name = "require_auth_consent")
    private Boolean requireAuthConsent;

    // PKCE required
    @Column(name = "require_proof_key")
    private Boolean requireProofKey;

    // Token endpoint authentication signing algorithm
    @Column(name = "token_endpoint_auth_signing_algorithm")
    private String tokenEndpointAuthSigningAlgorithm;

    // Refresh Token lifetime (minutes)
    @Column(name = "refresh_token_time_to_live")
    @JdbcTypeCode(SqlTypes.BIGINT)
    private Duration refreshTokenTimeToLive;

    // Authorization code lifetime (minutes)
    @Column(name = "authorization_code_time_to_live")
    @JdbcTypeCode(SqlTypes.BIGINT)
    private Duration authorizationCodeTimeToLive;

    // Device code lifetime (minutes)
    @Column(name = "device_code_time_to_live")
    @JdbcTypeCode(SqlTypes.BIGINT)
    private Duration deviceCodeTimeToLive;

    // ID Token lifetime (minutes)
    @Column(name = "id_token_time_to_live")
    @JdbcTypeCode(SqlTypes.BIGINT)
    private Duration idTokenTimeToLive;

    // Access Token lifetime (minutes)
    @Column(name = "access_token_time_to_live")
    @JdbcTypeCode(SqlTypes.BIGINT)
    private Duration accessTokenTimeToLive;

    // ID Token signing algorithm
    @Column(name = "id_token_signature_algorithm")
    private String idTokenSignatureAlgorithm;

    // Access Token format
    @Column(name = "access_token_format")
    private String accessTokenFormat;

    // Reuse refresh token
    @Column(name = "reuse_refresh_token")
    private Boolean reuseRefreshToken;
}
