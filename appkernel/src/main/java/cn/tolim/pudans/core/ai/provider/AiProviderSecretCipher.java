package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.core.infra.crypto.FileKeyCipher;
import cn.tolim.pudans.core.infra.properties.PudansProperties;
import org.springframework.stereotype.Component;

@Component
public class AiProviderSecretCipher {

    private static final String KEY_FILE = "ai-provider.key";

    private final FileKeyCipher cipher;

    public AiProviderSecretCipher(PudansProperties properties) {
        this.cipher = new FileKeyCipher(properties.getWorkDir().resolve("keys").resolve(KEY_FILE));
    }

    public boolean isEncrypted(String value) {
        return cipher.isEncrypted(value);
    }

    public String encrypt(String value) {
        return cipher.encrypt(value);
    }

    public String decrypt(String value) {
        return cipher.decrypt(value);
    }
}
