package cn.tolim.pudans.core.agent.tool;

import cn.tolim.pudans.contract.ai.dto.AiToolDescriptor;
import cn.tolim.pudans.core.ai.catalog.AiCapabilityRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class AgentToolCapabilities implements AiCapabilityRegistry {

    // 目录只暴露当前已注册到 AgentScope 的真实工具。
    @Override
    public Flux<AiToolDescriptor> tools() {
        return Flux.just(new AiToolDescriptor(
                GenerateMediaTool.NAME,
                "图片/视频生成",
                "创建真实的图片或视频生成任务，返回持久化任务标识。",
                true));
    }
}
