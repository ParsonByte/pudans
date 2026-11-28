package cn.tolim.pudans.iam.protocol.saml.store;

import cn.tolim.pudans.iam.protocol.saml.model.SamlAuthnRequest;

import java.time.Duration;


public interface SamlAuthnRequestStore {

    // Save an AuthnRequest by its ID.
    void save(SamlAuthnRequest authnRequest, Duration timeToLive);

    // Find an AuthnRequest by its ID.
    SamlAuthnRequest find(String id);

    // Remove and return an AuthnRequest by its ID.
    SamlAuthnRequest consume(String id);

    // Remove an AuthnRequest by its ID.
    void remove(String id);
}