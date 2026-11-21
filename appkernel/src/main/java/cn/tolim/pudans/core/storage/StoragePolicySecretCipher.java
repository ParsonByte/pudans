package cn.tolim.pudans.core.storage;

import org.springframework.stereotype.Component;
import cn.tolim.pudans.infra.crypto.FileKeyCipher;
import cn.tolim.pudans.infra.properties.PudansProperties;

@Component
public class StoragePolicySecretCipher {

    private static final String KEY_FILE = "storage-policy.key";

    private final FileKeyCipher cipher;

    // 使用工作目录下的独立密钥加密策略凭据。
    public StoragePolicySecretCipher(PudansProperties properties) {
        this.cipher = new FileKeyCipher(properties.getWorkDir().resolve("keys").resolve(KEY_FILE));
    }

    // 判断值是否已由本机密钥加密。
    public boolean isEncrypted(String value) {
        return cipher.isEncrypted(value);
    }

    // 使用策略密钥加密敏感凭据。
    public String encrypt(String value) {
        return cipher.encrypt(value);
    }

    // 使用策略密钥解密敏感凭据。
    public String decrypt(String value) {
        return cipher.decrypt(value);
    }
}
