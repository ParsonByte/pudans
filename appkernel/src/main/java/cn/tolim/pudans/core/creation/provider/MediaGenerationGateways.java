package cn.tolim.pudans.core.creation.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MediaGenerationGateways {

    private final Map<String, MediaGenerationGateway> gateways;

    // 注册全部真实渠道实现，并拒绝重复供应商。
    public MediaGenerationGateways(List<MediaGenerationGateway> gateways) {
        var indexed = new LinkedHashMap<String, MediaGenerationGateway>();
        for (var gateway : gateways) {
            if (indexed.putIfAbsent(gateway.provider(), gateway) != null) {
                throw new IllegalStateException("duplicate media generation provider: " + gateway.provider());
            }
        }
        this.gateways = Map.copyOf(indexed);
    }

    public Optional<MediaGenerationGateway> find(String provider) {
        return provider == null ? Optional.empty() : Optional.ofNullable(gateways.get(provider));
    }
}
