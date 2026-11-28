package cn.tolim.pudans.iam.protocol.saml.application;

import cn.tolim.pudans.iam.appkernel.core.AbstractCertApplicationService;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotConfigException;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.communal.entity.app.AppCertEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppCertUsingType;
import cn.tolim.pudans.iam.communal.repository.app.AppAccessPolicyRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppAccountRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppCertRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import cn.tolim.pudans.iam.protocol.saml.model.SamlConfigData;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.store.SamlConfigStore;

import java.time.Duration;
import java.util.Optional;


public abstract class AbstractSamlApplicationService extends AbstractCertApplicationService
        implements SamlApplicationService {

    protected final AppRepository appRepository;
    protected final SamlConfigStore samlConfigStore;

    protected AbstractSamlApplicationService(AppCertRepository appCertRepository,
                                             AppGroupAssociationRepository appGroupAssociationRepository,
                                             AppAccountRepository appAccountRepository,
                                             AppAccessPolicyRepository appAccessPolicyRepository,
                                             AppRepository appRepository,
                                             SamlConfigStore samlConfigStore) {
        super(appCertRepository, appGroupAssociationRepository, appAccountRepository,
                appAccessPolicyRepository, appRepository);
        this.appRepository = appRepository;
        this.samlConfigStore = samlConfigStore;
    }

    @Override
    public void delete(String appId) {
        // Delete the application and all its related records.
        appRepository.deleteById(appId);
        appCertRepository.deleteByAppId(appId);
        appAccountRepository.deleteAllByAppId(appId);
        appAccessPolicyRepository.deleteAllByAppId(appId);
        samlConfigStore.delete(appId);
    }

    @Override
    public SamlProtocolConfig getProtocolConfig(String appCode) {
        AppEntity app = appRepository.findByCode(appCode).orElseThrow(AppNotExistException::new);
        SamlConfigData data = samlConfigStore.find(app.getId());
        if (data == null) {
            throw new AppNotConfigException();
        }
        Optional<AppCertEntity> certificate = appCertRepository
                .findByAppIdAndUsingType(app.getId(), AppCertUsingType.OIDC_JWK);
        SamlNameIdFormat nameIdFormat = SamlNameIdFormat.getType(data.getNameIdFormat());
        SamlBindingType defaultBinding = SamlBindingType.getType(data.getDefaultBinding());
        return SamlProtocolConfig.builder()
                .appId(app.getId())
                .appName(app.getName())
                .clientId(app.getClientId())
                .clientSecret(app.getClientSecret())
                .appCode(app.getCode())
                .appTemplate(app.getTemplate())
                .entityId(data.getEntityId())
                .assertionConsumerServiceUrl(data.getAssertionConsumerServiceUrl())
                .singleLogoutServiceUrl(data.getSingleLogoutServiceUrl())
                .nameIdFormat(nameIdFormat)
                .defaultBinding(defaultBinding)
                .assertionTimeToLive(data.getAssertionTimeToLiveSeconds() == null
                        ? null : Duration.ofSeconds(data.getAssertionTimeToLiveSeconds()))
                .clockSkew(data.getClockSkewSeconds() == null
                        ? null : Duration.ofSeconds(data.getClockSkewSeconds()))
                .wantAssertionsSigned(data.getWantAssertionsSigned())
                .wantAuthnRequestsSigned(data.getWantAuthnRequestsSigned())
                .signingAlgorithm(data.getSigningAlgorithm())
                .signingCertificate(certificate.map(AppCertEntity::getCert).orElse(null))
                .signingPrivateKey(certificate.map(AppCertEntity::getPrivateKey).orElse(null))
                .attributeMappings(data.getAttributeMappings())
                .configured(app.getConfigured())
                .build();
    }
}