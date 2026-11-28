package cn.tolim.pudans.iam.appkernel.jwt;

import cn.tolim.pudans.iam.appkernel.core.AbstractCertApplicationService;
import cn.tolim.pudans.iam.appkernel.core.exception.AppCertNotExistException;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import cn.tolim.pudans.iam.communal.entity.app.AppCertEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppJwtConfigPO;
import cn.tolim.pudans.iam.communal.enums.app.AppCertUsingType;
import cn.tolim.pudans.iam.communal.repository.app.*;

import java.util.Objects;
import java.util.Optional;


public abstract class AbstractJwtApplicationService extends AbstractCertApplicationService
        implements JwtApplicationService {

    // AppCertRepository
    protected final AppCertRepository appCertRepository;
    // ApplicationRepository
    protected final AppRepository appRepository;

    protected final AppJwtConfigRepository appJwtConfigRepository;

    protected AbstractJwtApplicationService(AppJwtConfigRepository appJwtConfigRepository,
                                            AppGroupAssociationRepository appGroupAssociationRepository,
                                            AppCertRepository appCertRepository,
                                            AppRepository appRepository,
                                            AppAccountRepository appAccountRepository,
                                            AppAccessPolicyRepository appAccessPolicyRepository) {
        super(appCertRepository, appGroupAssociationRepository, appAccountRepository,
                appAccessPolicyRepository, appRepository);
        this.appCertRepository = appCertRepository;
        this.appRepository = appRepository;
        this.appJwtConfigRepository = appJwtConfigRepository;
    }

    @Override
    public void delete(String appId) {
        // Delete application
        appRepository.deleteById(appId);
        // Delete application account
        appAccountRepository.deleteAllByAppId(appId);
        // Delete application configuration
        appJwtConfigRepository.deleteByAppId(appId);
        // Delete certificate
        appCertRepository.deleteByAppId(appId);
    }

    @Override
    public JwtProtocolConfig getProtocolConfig(String appCode) {
        AppJwtConfigPO configPo = appJwtConfigRepository.findByAppCode(appCode);
        if (Objects.isNull(configPo)) {
            throw new AppNotExistException();
        }
        Optional<AppCertEntity> entity = appCertRepository
                .findByAppIdAndUsingType(configPo.getAppId(), AppCertUsingType.JWT_ENCRYPT);
        if (entity.isEmpty()) {
            throw new AppCertNotExistException();
        }

        JwtProtocolConfig.JwtProtocolConfigBuilder<?, ?> jwtProtocolConfig = JwtProtocolConfig
                .builder();

        jwtProtocolConfig.appId(String.valueOf(configPo.getAppId()))
            .clientId(configPo.getClientId())
            .appName(configPo.getAppName())
            .clientSecret(configPo.getClientSecret())
            .appCode(configPo.getAppCode())
            .appTemplate(configPo.getAppTemplate())
            .redirectUrl(configPo.getRedirectUrl())
            .targetLinkUrl(configPo.getTargetLinkUrl())
            .bindingType(configPo.getBindingType())
            .idTokenTimeToLive(Objects.toString(configPo.getIdTokenTimeToLive().toSeconds(),""))
            .idTokenSubjectType(configPo.getIdTokenSubjectType())
            .configured(configPo.getConfigured());

        entity.ifPresent(appCert -> {
            jwtProtocolConfig.jwtPrivateKey(appCert.getPrivateKey());
            jwtProtocolConfig.jwtPublicKey(appCert.getPublicKey());
        });
        return jwtProtocolConfig.build();
    }
}
