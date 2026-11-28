package cn.tolim.pudans.iam.communal.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class IdentitySourceProviderNotSupportedException extends PudansException {
    public IdentitySourceProviderNotSupportedException() {
        super("identity_source_provider_not_supported", "不支持该身份源提供商", DEFAULT_STATUS);
    }
}
