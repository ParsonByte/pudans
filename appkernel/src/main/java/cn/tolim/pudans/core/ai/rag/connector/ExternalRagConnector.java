package cn.tolim.pudans.core.ai.rag.connector;

import cn.tolim.pudans.contract.core.extension.ai.KnowledgeBase;
import reactor.core.publisher.Mono;

public interface ExternalRagConnector {

    KnowledgeBase.ExternalProvider provider();

    Mono<ExternalRagResult> retrieve(ExternalRagTarget target, ExternalRagRequest request);
}