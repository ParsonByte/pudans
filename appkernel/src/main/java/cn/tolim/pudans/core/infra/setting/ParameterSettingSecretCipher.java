package cn.tolim.pudans.core.infra.setting;

import org.springframework.stereotype.Component;
import cn.tolim.pudans.core.infra.crypto.FileKeyCipher;
import cn.tolim.pudans.core.infra.properties.PudansProperties;

@Component
public class ParameterSettingSecretCipher {

    private static final String KEY_FILE = "parameter-settings.key";

    private final FileKeyCipher cipher;

    public ParameterSettingSecretCipher(PudansProperties properties) {
        this.cipher = new FileKeyCipher(properties.getWorkDir().resolve("keys").resolve(KEY_FILE));
    }

    public boolean isEncrypted(String value) {
        return cipher.isEncrypted(value);
    }

    public String encrypt(String rawValue) {
        return cipher.encrypt(rawValue);
    }

    public String decrypt(String storedValue) {
        return cipher.decrypt(storedValue);
    }
}
