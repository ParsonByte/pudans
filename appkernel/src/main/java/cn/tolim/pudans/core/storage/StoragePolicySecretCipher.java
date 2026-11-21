package cn.tolim.pudans.core.storage;

import org.springframework.stereotype.Component;
import cn.tolim.pudans.infra.crypto.FileKeyCipher;
import cn.tolim.pudans.infra.properties.PudansProperties;

@Component
public class StoragePolicySecretCipher {

    private static final String KEY_FILE = "storage-policy.key";

    private final FileKeyCipher cipher;

    public StoragePolicySecretCipher(PudansProperties properties) {
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
