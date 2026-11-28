package cn.tolim.pudans.iam.protocol.saml.service;


public interface SamlEndpointResolver {

    // Identity provider entity ID for the application.
    String idpEntityId(String appCode);

    // Identity provider metadata URL for the application.
    String metadataUrl(String appCode);

    // Identity provider single sign-on URL for the application.
    String ssoUrl(String appCode);

    // Identity provider single logout URL for the application.
    String sloUrl(String appCode);
}