package cn.tolim.pudans.iam.protocol.cas.store;

import cn.tolim.pudans.iam.protocol.cas.model.CasConfigData;


public interface CasConfigStore {

    // Save the protocol configuration of an application.
    void save(String appId, CasConfigData config);

    // Find the protocol configuration of an application.
    CasConfigData find(String appId);

    // Delete the protocol configuration of an application.
    void delete(String appId);
}