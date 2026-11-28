package cn.tolim.pudans.iam.identitysource.core.provider;

import cn.tolim.pudans.iam.communal.jackjson.encrypt.EncryptionModule;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.exception.IdentitySourceNotConfiguredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;


public final class IdentitySourceProviderConfigUtils {

    // Decrypting mapper shared by all provider configurations
    private static final ObjectMapper OBJECT_MAPPER = EncryptionModule.deserializerDecrypt();

    private IdentitySourceProviderConfigUtils() {
    }

    // Deserialize a provider configuration, encrypted fields are decrypted by the encryption module
    public static IdentitySourceConfig readConfig(IdentitySourceProviderDefinition definition,
                                                  String basicConfig) {
        if (StringUtils.isBlank(basicConfig)) {
            throw new IdentitySourceNotConfiguredException();
        }
        try {
            return OBJECT_MAPPER.readValue(basicConfig, definition.getConfigType());
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Unable to read identity source configuration of provider ["
                            + definition.getCode() + "]", exception);
        }
    }
}