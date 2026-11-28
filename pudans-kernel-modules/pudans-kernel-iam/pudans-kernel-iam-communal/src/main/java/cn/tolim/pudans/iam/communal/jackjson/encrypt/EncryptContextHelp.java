package cn.tolim.pudans.iam.communal.jackjson.encrypt;

import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.infra.context.ApplicationContextService;
import cn.tolim.pudans.iam.infra.util.AesUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.util.Objects;

import static cn.tolim.pudans.iam.communal.constant.SettingConstants.AES_SECRET;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EncryptContextHelp {
    private static final AesUtils AES_UTILS = new AesUtils(getAesSecret());

    public static String encrypt(String content) {
        return AES_UTILS.encrypt(content);
    }

    public static String decrypt(String content) {
        if (Objects.isNull(content)) {
            return null;
        }
        return AES_UTILS.decrypt(content);
    }

    // Get AES key
    // Returns: String
    public static String getAesSecret() {
        SettingEntity setting = getSettingRepository().findByName(AES_SECRET);
        Assert.notNull(setting, "AES 密钥不能为空");
        return setting.getValue();
    }

    private static SettingRepository getSettingRepository() {
        return ApplicationContextService.getBean(SettingRepository.class);
    }
}
