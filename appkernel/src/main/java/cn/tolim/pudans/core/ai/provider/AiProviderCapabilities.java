package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.dto.AiProviderCapability;
import cn.tolim.pudans.contract.ai.dto.AiProviderCapabilityCode;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class AiProviderCapabilities {

    private AiProviderCapabilities() {}

    public static List<AiProviderCapability> available(
            Set<AiProviderCapabilityCode> supported) {
        if (supported == null || supported.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(AiProviderCapabilityCode.values())
                .filter(supported::contains)
                .map(code -> new AiProviderCapability(
                        code.code(), code.label(), AiProviderCapability.AVAILABLE))
                .toList();
    }
}
