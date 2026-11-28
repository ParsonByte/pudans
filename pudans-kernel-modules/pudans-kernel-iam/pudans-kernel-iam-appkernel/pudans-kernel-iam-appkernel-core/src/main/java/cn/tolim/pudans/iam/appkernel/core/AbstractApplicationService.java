package cn.tolim.pudans.iam.appkernel.core;

import cn.tolim.pudans.iam.communal.entity.app.AppAccountEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppGroupAssociationEntity;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.communal.exception.app.AppAccountNotExistException;
import cn.tolim.pudans.iam.communal.repository.app.AppAccountRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;

import java.util.ArrayList;
import java.util.List;


@Slf4j
public abstract class AbstractApplicationService implements ApplicationService {

    protected final ObjectMapper mapper = new ObjectMapper();
    // AppAccountRepository
    protected final AppAccountRepository appAccountRepository;
    // ApplicationRepository
    protected final AppRepository appRepository;
    // AppGroupAssociationRepository
    protected final AppGroupAssociationRepository appGroupAssociationRepository;
    // IdGenerator
    protected final IdGenerator idGenerator;

    protected AbstractApplicationService(AppAccountRepository appAccountRepository,
                                         AppGroupAssociationRepository appGroupAssociationRepository,
                                         AppRepository appRepository) {
        this.appAccountRepository = appAccountRepository;
        this.appGroupAssociationRepository = appGroupAssociationRepository;
        this.appRepository = appRepository;
        this.idGenerator = new AlternativeJdkIdGenerator();
    }

    // Get default application account
    @Override
    public AppAccount getDefaultAppAccount(String appId, String userId) {
        AppAccountEntity entity = appAccountRepository
                .findByAppIdAndUserIdAndDefaultedIsTrue(appId, userId)
                .orElseThrow(AppAccountNotExistException::new);
        AppAccount account = new AppAccount();
        account.setAppId(entity.getAppId());
        account.setPassword(entity.getPassword());
        account.setAccount(entity.getAccount());
        return account;
    }

    // Create application
    @Override
    public AppEntity createApp(String name, String icon, String remark,
                               AuthorizationType authorizationType) {
        AppEntity appEntity = new AppEntity();
        appEntity.setName(name);
        appEntity.setIcon(icon);
        appEntity.setCode(RandomStringUtils.randomAlphanumeric(32).toLowerCase());
        appEntity.setTemplate(getCode());
        appEntity.setType(getType());
        appEntity.setEnabled(true);
        appEntity.setConfigured(false);
        appEntity.setProtocol(getProtocol());
        appEntity.setClientId(idGenerator.generateId().toString().replace("-", ""));
        appEntity.setClientSecret(idGenerator.generateId().toString().replace("-", ""));
        appEntity.setAuthorizationType(authorizationType);
        appEntity.setRemark(remark);
        return appRepository.save(appEntity);
    }

    @Override
    public AppEntity createApp(String name, String icon, String remark, List<String> groups,
                               AuthorizationType authorizationType) {

        AppEntity appEntity = createApp(name, icon, remark, authorizationType);
        List<AppGroupAssociationEntity> list = new ArrayList<>();
        for (String id : groups) {
            AppGroupAssociationEntity appGroupAssociationEntity = new AppGroupAssociationEntity();
            appGroupAssociationEntity.setGroupId(id);
            appGroupAssociationEntity.setApp(appEntity);
            list.add(appGroupAssociationEntity);
        }
        appGroupAssociationRepository.saveAll(list);
        return appEntity;
    }
}
