package cn.tolim.pudans.iam.identitysource.core;


public interface IdentitySourceConfigValidator<T extends IdentitySourceConfig> {
    // validate
    // Parameter config T
    // Returns Boolean
    Boolean validate(T config);
}
