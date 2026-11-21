package cn.tolim.pudans.core.ai.memory.asset;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.ai.AiMemoryAsset;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MemoryAssetBindingResolver {

    private final ReactiveExtensionClient client;

    // 绑定优先级按资产创建时间与名称稳定排序，自身作用域由调用方先行处理。
    public Mono<List<MemoryContext>> borrowedScopes(MemoryContext origin) {
        return client.listAll(AiMemoryAsset.class, ListOptions.builder().build(), defaultSort())
                .filter(asset -> asset.getMetadata() != null
                        && asset.getSpec() != null
                        && asset.getSpec().getStatus() == AiMemoryAsset.Status.ACTIVE
                        && origin.teamId().equals(asset.getSpec().getTeam())
                        && asset.getSpec().getAgents() != null
                        && asset.getSpec().getAgents().contains(origin.agentId())
                        && StringUtils.hasText(asset.getSpec().getSourceUserId())
                        && StringUtils.hasText(asset.getSpec().getSourceAgentId()))
                .sort(Comparator.comparing(
                                (AiMemoryAsset asset) -> asset.getMetadata().getCreationTimestamp(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(asset -> asset.getMetadata().getName()))
                .map(asset -> new MemoryContext(
                        asset.getSpec().getTeam(),
                        asset.getSpec().getSourceUserId(),
                        asset.getSpec().getSourceAgentId(),
                        origin.sessionId(),
                        origin.taskId()))
                .collectList()
                .map(scopes -> dedupe(origin, scopes));
    }

    private static List<MemoryContext> dedupe(MemoryContext origin, List<MemoryContext> scopes) {
        var keys = new LinkedHashSet<String>();
        keys.add(scopeKey(origin));
        var result = new ArrayList<MemoryContext>();
        for (var scope : scopes) {
            if (keys.add(scopeKey(scope))) {
                result.add(scope);
            }
        }
        return List.copyOf(result);
    }

    private static String scopeKey(MemoryContext context) {
        return context.teamId() + "\n" + context.userId() + "\n" + context.agentId();
    }
}