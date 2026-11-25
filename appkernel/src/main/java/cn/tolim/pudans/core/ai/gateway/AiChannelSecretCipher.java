package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.infra.crypto.FileKeyCipher;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.properties.PudansProperties;
import org.springframework.stereotype.Component;

@Component
public class AiChannelSecretCipher {

    private final FileKeyCipher cipher;

    public AiChannelSecretCipher(AiProperties aiProperties, PudansProperties pudansProperties) {
        this.cipher = new FileKeyCipher(
                pudansProperties.getWorkDir().resolve(aiProperties.getCredentialKeyFile()));
    }

    public String encrypt(String value) {
        return cipher.encrypt(value);
    }

    public String decrypt(String value) {
        return cipher.decrypt(value);
    }
}
