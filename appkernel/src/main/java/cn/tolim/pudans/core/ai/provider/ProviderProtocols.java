package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;

public final class ProviderProtocols {

    private ProviderProtocols() {}

    public static AiProvider.Protocol of(AiProvider provider, AiProviderAccount account) {
        var spec = account == null ? null : account.getSpec();
        var override = spec == null ? null : spec.getProtocolOverride();
        if (override != null) {
            return override;
        }
        if (provider == null || provider.getSpec() == null) {
            throw new IllegalArgumentException("供应商协议不能为空。");
        }
        return provider.getSpec().getProtocol();
    }
}
