package cn.tolim.pudans.core.ai.rag.connector;

import cn.tolim.pudans.contract.core.extension.ai.KnowledgeBase;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseCodes;
import cn.tolim.pudans.core.ai.rag.RetrievalCandidate;
import cn.tolim.pudans.core.ai.rag.RetrievalOptions;
import cn.tolim.pudans.core.ai.rag.RetrievedChunk;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ExternalRagRetriever {

    private final ExternalRagConnectorResolver connectorResolver;

    private final AiProviderAccountService accountService;

    public static boolean isExternal(KnowledgeBase knowledgeBase) {
        return knowledgeBase != null
                && knowledgeBase.getSpec() != null
                && knowledgeBase.getSpec().getExternal() != null;
    }

    public Mono<List<RetrievedChunk>> retrieve(
            KnowledgeBase knowledgeBase, String query, RetrievalOptions options) {
        if (knowledgeBase == null || knowledgeBase.getSpec() == null) {
            return Mono.error(new IllegalArgumentException("知识库配置不能为空。"));
        }
        var external = knowledgeBase.getSpec().getExternal();
        if (external == null) {
            return Mono.just(List.of());
        }
        if (external.getProvider() == null) {
            return Mono.error(configError(external, "未配置 provider。"));
        }
        if (!StringUtils.hasText(external.getEndpoint())) {
            return Mono.error(configError(external, "未配置 endpoint。"));
        }
        if (options == null) {
            return Mono.error(new IllegalArgumentException("检索参数不能为空。"));
        }
        var topK = external.getTopK() == null ? options.topK() : external.getTopK();
        if (topK < 1) {
            return Mono.error(configError(external, "topK 必须大于 0。"));
        }
        var threshold = external.getScoreThreshold() == null
                ? options.scoreThreshold()
                : external.getScoreThreshold();
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
            return Mono.error(configError(external, "scoreThreshold 必须在 0 到 1 之间。"));
        }
        var provider = external.getProvider();
        var request = new ExternalRagRequest(query, topK, threshold, external.getFilters());
        return resolveApiKey(external)
                .defaultIfEmpty("")
                .map(apiKey -> new ExternalRagTarget(
                        external.getEndpoint().strip(),
                        external.getDataset(),
                        external.getKnowledgeBaseId(),
                        apiKey,
                        external.getRetrievePath()))
                .flatMap(target -> connectorResolver.connector(provider).retrieve(target, request))
                .map(result -> toChunks(knowledgeBase, provider, result, topK));
    }

    private Mono<String> resolveApiKey(KnowledgeBase.External external) {
        var credentialRef = external.getCredentialRef();
        if (!StringUtils.hasText(credentialRef)) {
            return Mono.empty();
        }
        return accountService.get(credentialRef.strip())
                .switchIfEmpty(Mono.error(configError(external, "凭据引用不存在。")))
                .flatMap(account -> accountService.apiKey(account)
                        .switchIfEmpty(Mono.error(configError(external, "凭据引用未配置 API Key。"))));
    }

    private static List<RetrievedChunk> toChunks(
            KnowledgeBase knowledgeBase,
            KnowledgeBase.ExternalProvider provider,
            ExternalRagResult result,
            int topK) {
        var kbName = KnowledgeBaseCodes.codeOf(knowledgeBase);
        var documents = result == null ? List.<ExternalRagDocument>of() : result.documents();
        var ranked = documents.stream()
                .filter(Objects::nonNull)
                .filter(document -> StringUtils.hasText(document.content()))
                .filter(document -> StringUtils.hasText(document.documentName())
                        || StringUtils.hasText(document.documentId()))
                .sorted(Comparator.comparingDouble(ExternalRagDocument::score).reversed())
                .limit(topK)
                .toList();
        var chunks = new ArrayList<RetrievedChunk>(ranked.size());
        for (var document : ranked) {
            var fileName = StringUtils.hasText(document.documentName())
                    ? document.documentName().strip()
                    : document.documentId().strip();
            var documentKey = kbName + '|' + provider + '|' + document.documentId() + '|' + fileName;
            var chunkKey = kbName + '|' + provider + '|' + document.documentId() + '|'
                    + (StringUtils.hasText(document.chunkId()) ? document.chunkId() : document.content());
            var score = Math.max(0.0, document.score());
            chunks.add(new RetrievedChunk(
                    new RetrievalCandidate(
                            stableId(chunkKey),
                            stableId(documentKey),
                            kbName,
                            fileName,
                            document.content(),
                            document.pageNo(),
                            document.headingPath(),
                            score),
                    score));
        }
        return List.copyOf(chunks);
    }

    private static long stableId(String identity) {
        var id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits()
                & Long.MAX_VALUE;
        return id == 0 ? 1 : id;
    }

    private static ExternalRagException configError(KnowledgeBase.External external, String message) {
        var provider = external == null || external.getProvider() == null
                ? "UNKNOWN"
                : external.getProvider().name();
        return new ExternalRagException("外部 RAG 连接器 " + provider + " 配置错误：" + message);
    }
}