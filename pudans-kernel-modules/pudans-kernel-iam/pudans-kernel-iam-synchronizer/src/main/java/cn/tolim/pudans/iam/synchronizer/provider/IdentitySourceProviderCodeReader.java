package cn.tolim.pudans.iam.synchronizer.provider;

import java.util.Map;


public interface IdentitySourceProviderCodeReader {

    // Read the persisted provider code of every identity source
    Map<String, String> findAllProviderCodes();

    // Read the persisted provider code of one identity source
    default String findProviderCode(String id) {
        return findAllProviderCodes().get(id);
    }
}