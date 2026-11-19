package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class AiProviderAdapterRegistry {

    private final Map<AiProvider.Protocol, AiProviderAdapter> adapters;

    public AiProviderAdapterRegistry(List<AiProviderAdapter> adapters) {
        var byProtocol = new HashMap<AiProvider.Protocol, AiProviderAdapter>();
        for (var adapter : Objects.requireNonNull(adapters, "供应商适配器列表不能为空")) {
            var protocol = Objects.requireNonNull(adapter.protocol(), "供应商适配器协议不能为空");
            if (byProtocol.putIfAbsent(protocol, adapter) != null) {
                throw new IllegalArgumentException("重复的供应商适配器: " + protocol);
            }
        }
        this.adapters = Map.copyOf(byProtocol);
    }

    public AiProviderAdapter require(AiProvider.Protocol protocol) {
        var adapter = protocol == null ? null : adapters.get(protocol);
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的供应商协议: " + protocol);
        }
        return adapter;
    }
}
