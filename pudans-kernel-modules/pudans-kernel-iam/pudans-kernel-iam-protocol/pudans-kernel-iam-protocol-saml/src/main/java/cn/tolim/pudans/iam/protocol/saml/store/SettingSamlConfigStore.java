package cn.tolim.pudans.iam.protocol.saml.store;

import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.protocol.saml.model.SamlConfigData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_CONFIG_DESCRIPTION;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_CONFIG_KEY_PREFIX;


@Component
public class SettingSamlConfigStore implements SamlConfigStore {

    private final SettingRepository settingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SettingSamlConfigStore(SettingRepository settingRepository) {
        Assert.notNull(settingRepository, "settingRepository must not be null");
        this.settingRepository = settingRepository;
    }

    @Override
    public void save(String appId, SamlConfigData config) {
        Assert.hasText(appId, "appId must not be blank");
        Assert.notNull(config, "config must not be null");
        String name = key(appId);
        SettingEntity entity = settingRepository.findByName(name);
        if (entity == null) {
            entity = new SettingEntity();
            entity.setName(name);
        }
        entity.setValue(write(config));
        entity.setDesc(SAML_CONFIG_DESCRIPTION);
        settingRepository.save(entity);
    }

    @Override
    public SamlConfigData find(String appId) {
        if (StringUtils.isBlank(appId)) {
            return null;
        }
        SettingEntity entity = settingRepository.findByName(key(appId));
        if (entity == null || StringUtils.isBlank(entity.getValue())) {
            return null;
        }
        return read(entity.getValue());
    }

    @Override
    public void delete(String appId) {
        if (StringUtils.isNotBlank(appId)) {
            settingRepository.deleteByName(key(appId));
        }
    }

    private String key(String appId) {
        return SAML_CONFIG_KEY_PREFIX + appId;
    }

    private String write(SamlConfigData config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception exception) {
            throw new PudansException("Unable to serialize SAML configuration", exception);
        }
    }

    private SamlConfigData read(String value) {
        try {
            return objectMapper.readValue(value, SamlConfigData.class);
        } catch (Exception exception) {
            throw new PudansException("Unable to deserialize SAML configuration", exception);
        }
    }
}