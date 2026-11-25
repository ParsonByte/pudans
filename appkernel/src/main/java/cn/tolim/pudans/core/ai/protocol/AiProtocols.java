package cn.tolim.pudans.core.ai.protocol;

import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiProtocols {

    private final Map<AiProtocol, AiProtocolDescriptor> descriptors;

    private final Map<AiProtocol, AiChatProtocol> chatProtocols;

    public AiProtocols(List<AiProtocolDescriptor> descriptors) {
        var indexed = new LinkedHashMap<AiProtocol, AiProtocolDescriptor>();
        var chatIndexed = new LinkedHashMap<AiProtocol, AiChatProtocol>();
        for (var descriptor : descriptors) {
            indexed.put(descriptor.protocol(), descriptor);
            if (descriptor instanceof AiChatProtocol chatProtocol) {
                chatIndexed.put(descriptor.protocol(), chatProtocol);
            }
        }
        this.descriptors = Map.copyOf(indexed);
        this.chatProtocols = Map.copyOf(chatIndexed);
    }

    public List<AiProtocolDescriptor> list() {
        return descriptors.values().stream()
                .sorted(Comparator.comparing(descriptor -> descriptor.protocol().value()))
                .toList();
    }

    public Optional<AiProtocolDescriptor> find(String value) {
        return AiProtocol.find(value).map(descriptors::get).filter(java.util.Objects::nonNull);
    }

    public AiProtocolDescriptor require(String value) {
        return find(value)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "ai-protocol-unsupported",
                        "不支持的 AI 协议：" + value + "。"));
    }

    public Optional<AiChatProtocol> findChat(String value) {
        return AiProtocol.find(value)
                .filter(chatProtocols::containsKey)
                .map(chatProtocols::get);
    }

    public AiChatProtocol requireChat(String value) {
        return findChat(value)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "agent-channel-protocol-unsupported",
                        "该 AI 协议不能作为聊天运行时使用：" + value + "。"));
    }
}
