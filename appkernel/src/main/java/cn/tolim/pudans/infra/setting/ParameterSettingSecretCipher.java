package cn.tolim.pudans.infra.setting;

import org.springframework.stereotype.Component;
import cn.tolim.pudans.infra.crypto.FileKeyCipher;
import cn.tolim.pudans.infra.properties.PudansProperties;

@Component
public class ParameterSettingSecretCipher {

    private static final String KEY_FILE = "parameter-settings.key";

    private final FileKeyCipher cipher;

    // 基于工作目录中的密钥文件构建加密器。
    public ParameterSettingSecretCipher(PudansProperties properties) {
        this.cipher = new FileKeyCipher(properties.getWorkDir().resolve("keys").resolve(KEY_FILE));
    }

    // 判断值是否已由本机密钥加密。
    public boolean isEncrypted(String value) {
        return cipher.isEncrypted(value);
    }

    // 使用本机密钥加密敏感配置值。
    public String encrypt(String rawValue) {
        return cipher.encrypt(rawValue);
    }

    // 解密已加密的配置值。
    public String decrypt(String storedValue) {
        return cipher.decrypt(storedValue);
    }
}
