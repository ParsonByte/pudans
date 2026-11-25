package cn.tolim.pudans.core.ai.catalog;

import cn.tolim.pudans.contract.ai.dto.AiSkillDescriptor;
import cn.tolim.pudans.contract.ai.dto.AiToolDescriptor;
import reactor.core.publisher.Flux;

public interface AiCapabilityRegistry {

    default Flux<AiToolDescriptor> tools() {
        return Flux.empty();
    }

    default Flux<AiSkillDescriptor> skills() {
        return Flux.empty();
    }
}
