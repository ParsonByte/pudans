package cn.tolim.pudans.iam.appkernel.oidc;

import cn.tolim.pudans.iam.appkernel.core.AbstractCertApplicationService;
import cn.tolim.pudans.iam.appkernel.core.exception.AppCertNotExistException;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.appkernel.oidc.model.OidcProtocolConfig;
import cn.tolim.pudans.iam.communal.entity.app.AppCertEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppOidcConfigPO;
import cn.tolim.pudans.iam.communal.repository.app.*;
import com.nimbusds.jose.jwk.RSAKey;

import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import static cn.tolim.pudans.iam.communal.enums.app.AppCertUsingType.OIDC_JWK;
import static cn.tolim.pudans.iam.infra.util.CertUtils.readPrivateKey;
import static cn.tolim.pudans.iam.infra.util.CertUtils.readPublicKey;


public abstract class AbstractOidcApplicationService extends AbstractCertApplicationService
        implements OidcApplicationService {

    // AppCertRepository
    protected final AppCertRepository appCertRepository;
    // ApplicationRepository
    protected final AppRepository appRepository;
    // AppOidcConfigRepository
    protected final AppOidcConfigRepository appOidcConfigRepository;
    protected AbstractOidcApplicationService(AppCertRepository appCertRepository,
                                             AppGroupAssociationRepository appGroupAssociationRepository,
                                             AppAccountRepository appAccountRepository,
                                             AppAccessPolicyRepository appAccessPolicyRepository,
                                             AppRepository appRepository,
                                             AppOidcConfigRepository appOidcConfigRepository) {
        super(appCertRepository, appGroupAssociationRepository, appAccountRepository,
                appAccessPolicyRepository, appRepository);
        this.appCertRepository = appCertRepository;
        this.appRepository = appRepository;
        this.appOidcConfigRepository = appOidcConfigRepository;
    }

    @Override
    public void delete(String appId) {
        // Delete application
        appRepository.deleteById(appId);
        // Delete certificate
        appCertRepository.deleteByAppId(appId);
        // Delete application account
        appAccountRepository.deleteAllByAppId(appId);
        // Delete application permission policy
        appAccessPolicyRepository.deleteAllByAppId(appId);
        // Delete OIDC configuration
        appOidcConfigRepository.deleteByAppId(appId);
    }

    // Get protocol configuration
    @Override
    public OidcProtocolConfig getProtocolConfig(String appCode) {
        AppOidcConfigPO appConfig = appOidcConfigRepository.findByAppCode(appCode);
        if (Objects.isNull(appConfig)) {
            throw new AppNotExistException();
        }
        Optional<AppCertEntity> appCertOptional = appCertRepository
                .findByAppIdAndUsingType(appConfig.getAppId(), OIDC_JWK);
        if (appCertOptional.isEmpty()) {
            throw new AppCertNotExistException();
        }
        AppCertEntity appCert = appCertOptional.get();
        try {

            PrivateKey rsaPrivateKey = readPrivateKey(appCert.getPrivateKey(), "");
            RSAPublicKey rsaPublicKey = (RSAPublicKey) readPublicKey(appCert.getPublicKey(), "");

            RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey)
                    .privateKey(rsaPrivateKey)
                    .keyID(appCert.getId())
                    .build();

            return OidcProtocolConfig.builder()
                    .appId(appConfig.getAppId())
                    .appName(appConfig.getAppName())
                    .clientId(appConfig.getClientId())
                    .clientSecret(appConfig.getClientSecret())
                    .appCode(appConfig.getAppCode())
                    .appTemplate(appConfig.getAppTemplate())
                    .clientAuthMethods(appConfig.getClientAuthMethods())
                    .authGrantTypes(appConfig.getAuthGrantTypes())
                    .responseTypes(appConfig.getResponseTypes())
                    .redirectUris(appConfig.getRedirectUris())
                    .postLogoutRedirectUris(appConfig.getPostLogoutRedirectUris())
                    .grantScopes(appConfig.getGrantScopes())
                    .requireAuthConsent(appConfig.getRequireAuthConsent())
                    .requireProofKey(appConfig.getRequireProofKey())
                    .tokenEndpointAuthSigningAlgorithm(appConfig.getTokenEndpointAuthSigningAlgorithm())
                    .refreshTokenTimeToLive(appConfig.getRefreshTokenTimeToLive())
                    .authorizationCodeTimeToLive(appConfig.getAuthorizationCodeTimeToLive())
                    .deviceCodeTimeToLive(appConfig.getDeviceCodeTimeToLive())
                    .idTokenSignatureAlgorithm(appConfig.getIdTokenSignatureAlgorithm())
                    .idTokenTimeToLive(appConfig.getIdTokenTimeToLive())
                    .accessTokenFormat(appConfig.getAccessTokenFormat())
                    .accessTokenTimeToLive(appConfig.getAccessTokenTimeToLive())
                    .reuseRefreshToken(appConfig.getReuseRefreshToken())
                    .jwks(Collections.singletonList(rsaKey))
                    .configured(appConfig.getConfigured())
                    .build();

        } catch (Exception ex) {
            throw new IllegalStateException("选择 JWK 失败 -> " + ex.getMessage(),
                    ex);
        }

    }
}
