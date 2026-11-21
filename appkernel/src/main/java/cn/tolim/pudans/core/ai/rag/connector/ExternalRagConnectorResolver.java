package cn.tolim.pudans.core.ai.rag.connector;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExternalRagConnectorResolver {

    private final Map<KnowledgeBase.ExternalProvider, ExternalRagConnector> connectors;

    public ExternalRagConnectorResolver(List<ExternalRagConnector> connectors) {
        var byProvider = new EnumMap<KnowledgeBase.ExternalProvider, ExternalRagConnector>(
                KnowledgeBase.ExternalProvider.class);
        for (var connector : connectors) {
            var previous = byProvider.put(connector.provider(), connector);
            if (previous != null) {
                throw new IllegalStateException("外部 RAG 连接器重复注册：" + connector.provider());
            }
        }
        this.connectors = Map.copyOf(byProvider);
    }

    public ExternalRagConnector connector(KnowledgeBase.ExternalProvider provider) {
        var connector = provider == null ? null : connectors.get(provider);
        if (connector == null) {
            throw new ExternalRagException("未支持的外部 RAG 提供方：" + provider);
        }
        return connector;
    }
}