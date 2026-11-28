package cn.tolim.pudans.iam.protocol.saml.store;

import cn.tolim.pudans.iam.protocol.saml.model.SamlConfigData;


public interface SamlConfigStore {

    // Save the protocol configuration of an application.
    void save(String appId, SamlConfigData config);

    // Find the protocol configuration of an application.
    SamlConfigData find(String appId);

    // Delete the protocol configuration of an application.
    void delete(String appId);
}