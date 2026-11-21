package cn.tolim.pudans.core.ai.model;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AiModelAdapterRegistry {

    private final Map<AiProvider.Protocol, List<AiModelAdapter>> adapters;

    public AiModelAdapterRegistry(List<AiModelAdapter> adapters) {
        var byProtocol = new EnumMap<AiProvider.Protocol, List<AiModelAdapter>>(AiProvider.Protocol.class);
        for (var adapter : Objects.requireNonNull(adapters, "模型适配器列表不能为空")) {
            for (var protocol : adapter.protocols()) {
                byProtocol.computeIfAbsent(protocol, key -> new ArrayList<>()).add(adapter);
            }
        }
        this.adapters = Map.copyOf(byProtocol);
    }

    public AiModelAdapter require(AiProvider.Protocol protocol, AiModel.Capability capability) {
        if (protocol == null || capability == null) {
            throw new IllegalArgumentException("供应商协议和模型能力不能为空");
        }
        var candidates = adapters.getOrDefault(protocol, List.of());
        for (var adapter : candidates) {
            if (adapter.capabilities().contains(capability)) {
                return adapter;
            }
        }
        throw new IllegalArgumentException("供应商协议 " + protocol + " 不支持模型能力 " + capability);
    }

    public boolean supports(AiProvider.Protocol protocol, AiModel.Capability capability) {
        if (protocol == null || capability == null) {
            return false;
        }
        return adapters.getOrDefault(protocol, List.of()).stream()
                .anyMatch(adapter -> adapter.capabilities().contains(capability));
    }
}
