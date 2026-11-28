package cn.tolim.pudans.iam.protocol.cas.application;

import cn.tolim.pudans.iam.appkernel.core.AbstractApplicationService;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotConfigException;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppAccessPolicyRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppAccountRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import cn.tolim.pudans.iam.protocol.cas.model.CasConfigData;
import cn.tolim.pudans.iam.protocol.cas.model.CasProtocolConfig;
import cn.tolim.pudans.iam.protocol.cas.store.CasConfigStore;

import java.time.Duration;


public abstract class AbstractCasApplicationService extends AbstractApplicationService
        implements CasApplicationService {

    protected final AppRepository appRepository;
    protected final AppAccessPolicyRepository appAccessPolicyRepository;
    protected final CasConfigStore casConfigStore;

    protected AbstractCasApplicationService(AppAccountRepository appAccountRepository,
                                            AppGroupAssociationRepository appGroupAssociationRepository,
                                            AppRepository appRepository,
                                            AppAccessPolicyRepository appAccessPolicyRepository,
                                            CasConfigStore casConfigStore) {
        super(appAccountRepository, appGroupAssociationRepository, appRepository);
        this.appRepository = appRepository;
        this.appAccessPolicyRepository = appAccessPolicyRepository;
        this.casConfigStore = casConfigStore;
    }

    @Override
    public void delete(String appId) {
        appRepository.deleteById(appId);
        appAccountRepository.deleteAllByAppId(appId);
        appAccessPolicyRepository.deleteAllByAppId(appId);
        casConfigStore.delete(appId);
    }

    @Override
    public CasProtocolConfig getProtocolConfig(String appCode) {
        AppEntity app = appRepository.findByCode(appCode).orElseThrow(AppNotExistException::new);
        CasConfigData data = casConfigStore.find(app.getId());
        if (data == null) {
            throw new AppNotConfigException();
        }
        return CasProtocolConfig.builder()
                .appId(app.getId())
                .appName(app.getName())
                .clientId(app.getClientId())
                .clientSecret(app.getClientSecret())
                .appCode(app.getCode())
                .appTemplate(app.getTemplate())
                .serviceUrls(data.getServiceUrls())
                .ticketTimeToLive(data.getTicketTimeToLiveSeconds() == null
                        ? null : Duration.ofSeconds(data.getTicketTimeToLiveSeconds()))
                .ticketGrantingTicketTimeToLive(
                        data.getTicketGrantingTicketTimeToLiveSeconds() == null ? null
                                : Duration.ofSeconds(data.getTicketGrantingTicketTimeToLiveSeconds()))
                .renewEnabled(data.getRenewEnabled())
                .secureCookie(data.getSecureCookie())
                .cookieName(data.getCookieName())
                .defaultServiceUrl(data.getDefaultServiceUrl())
                .attributeMappings(data.getAttributeMappings())
                .configured(app.getConfigured())
                .build();
    }
}