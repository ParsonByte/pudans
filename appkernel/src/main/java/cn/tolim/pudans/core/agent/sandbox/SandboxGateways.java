package cn.tolim.pudans.core.agent.sandbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SandboxGateways {

    private final Map<String, SandboxGateway> gateways;

    // 注册真实沙箱适配器并拒绝重复供应商。
    public SandboxGateways(List<SandboxGateway> gateways) {
        var indexed = new LinkedHashMap<String, SandboxGateway>();
        for (var gateway : gateways) {
            if (indexed.putIfAbsent(gateway.provider(), gateway) != null) {
                throw new IllegalStateException("duplicate sandbox provider: " + gateway.provider());
            }
        }
        this.gateways = Map.copyOf(indexed);
    }

    public Optional<SandboxGateway> find(String provider) {
        return provider == null ? Optional.empty() : Optional.ofNullable(gateways.get(provider));
    }
}
