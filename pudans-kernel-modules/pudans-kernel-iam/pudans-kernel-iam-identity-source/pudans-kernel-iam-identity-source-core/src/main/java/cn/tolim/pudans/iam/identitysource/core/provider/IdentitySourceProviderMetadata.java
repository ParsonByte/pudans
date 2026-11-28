package cn.tolim.pudans.iam.identitysource.core.provider;

import java.io.Serializable;


public record IdentitySourceProviderMetadata(String providerCode, String providerName,
                                             String rootExternalId, String dataOriginType,
                                             String dataOriginName) implements Serializable {
}