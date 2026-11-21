package cn.tolim.pudans.core.ai.memory.asset;

import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryAssetRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MemoryLayers {

    public static final String CODE_LAYER_NOT_FOUND = "ai-memory-layer-not-found";

    public static final String CODE_LAYER_INVALID = "ai-memory-layer-invalid";

    private static final int MAX_LIMIT = 200;

    private static final int MAX_MESSAGE_IDS = 5000;

    private static final int MAX_SESSION_IDS = 100;

    private static final int MAX_CONTENT_LENGTH = 200000;

    private static final int MAX_PATH_LENGTH = 128;

    private final MemoryAssets memoryAssets;

    private final AiMemoryAssetRepository assetRepository;

    private final AiMemoryL1Repository l1Repository;

    private final AiMemoryL2Repository l2Repository;

    private final AiMemoryL3Repository l3Repository;

    public Flux<Message> listL0(
            String userId,
            String name,
            @Nullable String team,
            long beforeId,
            @Nullable Instant from,
            @Nullable Instant to,
            @Nullable String query,
            int limit) {
        validateCursor(beforeId, limit);
        return memoryAssets
                .requireRead(userId, name, team)
                .flatMapMany(ref -> assetRepository.listMessages(
                        ref.spec().getTeam(),
                        ref.spec().getSourceUserId(),
                        ref.spec().getSourceAgentId(),
                        beforeId,
                        from,
                        to,
                        normalizeQuery(query),
                        limit));
    }

    public Flux<MemoryL1Item> listL1(
            String userId,
            String name,
            @Nullable String team,
            long beforeId,
            @Nullable Instant from,
            @Nullable Instant to,
            @Nullable String query,
            int limit) {
        validateCursor(beforeId, limit);
        return memoryAssets
                .requireRead(userId, name, team)
                .flatMapMany(ref -> l1Repository.listByScope(
                        ref.spec().getTeam(),
                        ref.spec().getSourceAgentId(),
                        ref.spec().getSourceUserId(),
                        from,
                        to,
                        normalizeQuery(query),
                        beforeId,
                        limit));
    }

    public Flux<MemoryL2Item> listL2(String userId, String name, @Nullable String team, long beforeId, int limit) {
        validateCursor(beforeId, limit);
        return memoryAssets
                .requireRead(userId, name, team)
                .flatMapMany(ref -> l2Repository
                        .listByScope(ref.spec().getTeam(), ref.spec().getSourceAgentId(), beforeId, limit)
                        .map(MemoryLayers::toItem));
    }

    public Mono<MemoryL2Detail> detailL2(
            String userId, String name, @Nullable String team, String path) {
        var sceneKey = validatePath(path);
        return memoryAssets
                .requireRead(userId, name, team)
                .flatMap(ref -> l2Repository
                        .findByKey(ref.spec().getTeam(), ref.spec().getSourceAgentId(), sceneKey)
                        .map(MemoryLayers::toDetail)
                        .switchIfEmpty(Mono.error(layerNotFound())));
    }

    public Mono<MemoryL3Content> detailL3(String userId, String name, @Nullable String team) {
        return memoryAssets
                .requireRead(userId, name, team)
                .flatMap(ref -> l3Repository
                        .findByScope(ref.spec().getTeam(), ref.spec().getSourceAgentId())
                        .map(MemoryLayers::toContent)
                        .defaultIfEmpty(new MemoryL3Content(null, "", null)));
    }

    public Mono<MemoryL1Item> updateL1(
            String userId, String name, @Nullable String team, long id, @Nullable String content) {
        var normalized = validateContent(content);
        return memoryAssets
                .requireOwner(userId, name, team)
                .flatMap(ref -> l1Repository
                        .updateContent(
                                ref.spec().getTeam(),
                                ref.spec().getSourceAgentId(),
                                ref.spec().getSourceUserId(),
                                id,
                                normalized)
                        .switchIfEmpty(Mono.error(layerNotFound())));
    }

    public Mono<MemoryL2Detail> updateL2(
            String userId,
            String name,
            @Nullable String team,
            String id,
            @Nullable String path,
            @Nullable String content,
            @Nullable String summary) {
        var normalized = validateContent(content);
        return memoryAssets.requireOwner(userId, name, team).flatMap(ref -> {
            var teamId = ref.spec().getTeam();
            var agentId = ref.spec().getSourceAgentId();
            var target = StringUtils.hasText(path)
                    ? l2Repository.findByKey(teamId, agentId, validatePath(path))
                    : findL2ById(teamId, agentId, id);
            return target
                    .switchIfEmpty(Mono.error(layerNotFound()))
                    .flatMap(block -> l2Repository.updateContent(
                            teamId,
                            agentId,
                            block.id(),
                            StringUtils.hasText(summary) ? summary.strip() : block.title(),
                            MemoryL2Content.update(block.summary(), normalized)))
                    .map(MemoryLayers::toDetail);
        });
    }

    public Mono<MemoryL3Content> updateL3(
            String userId, String name, @Nullable String team, @Nullable String content) {
        var normalized = validateContent(content);
        return memoryAssets
                .requireOwner(userId, name, team)
                .flatMap(ref -> l3Repository
                        .overwriteText(ref.spec().getTeam(), ref.spec().getSourceAgentId(), normalized))
                .map(MemoryLayers::toContent);
    }

    public Mono<Long> deleteL0(
            String userId,
            String name,
            @Nullable String team,
            @Nullable List<Long> messageIds,
            @Nullable List<String> sessionIds) {
        var ids = messageIds == null ? List.<Long>of() : messageIds.stream().filter(id -> id != null).toList();
        var sessions = sessionIds == null
                ? List.<String>of()
                : sessionIds.stream().filter(StringUtils::hasText).map(String::strip).toList();
        if (ids.size() > MAX_MESSAGE_IDS) {
            return Mono.error(invalid("messageIds 单次最多 5000 条。"));
        }
        if (sessions.size() > MAX_SESSION_IDS) {
            return Mono.error(invalid("sessionIds 单次最多 100 个。"));
        }
        return memoryAssets.requireOwner(userId, name, team).flatMap(ref -> {
            var spec = ref.spec();
            if (ids.isEmpty() && sessions.isEmpty()) {
                return assetRepository.deleteMessagesByScope(
                        spec.getTeam(), spec.getSourceUserId(), spec.getSourceAgentId());
            }
            return assetRepository.deleteMessages(
                    spec.getTeam(), spec.getSourceUserId(), spec.getSourceAgentId(), ids, sessions);
        });
    }

    public Mono<Long> deleteL1(String userId, String name, @Nullable String team, @Nullable List<Long> ids) {
        var normalized = ids == null ? List.<Long>of() : ids.stream().filter(id -> id != null).toList();
        if (normalized.size() > MAX_MESSAGE_IDS) {
            return Mono.error(invalid("ids 单次最多 5000 条。"));
        }
        return memoryAssets.requireOwner(userId, name, team).flatMap(ref -> {
            var spec = ref.spec();
            if (normalized.isEmpty()) {
                return l1Repository.deleteByScope(
                        spec.getTeam(), spec.getSourceAgentId(), spec.getSourceUserId());
            }
            return l1Repository.deleteByIds(
                    spec.getTeam(), spec.getSourceAgentId(), spec.getSourceUserId(), normalized);
        });
    }

    private Mono<L2SceneBlock> findL2ById(String teamId, String agentId, String id) {
        if (!StringUtils.hasText(id)) {
            return Mono.empty();
        }
        try {
            return l2Repository.findById(teamId, agentId, Long.parseLong(id.strip()));
        } catch (NumberFormatException error) {
            return l2Repository.findByKey(teamId, agentId, validatePath(id));
        }
    }

    private static MemoryL2Item toItem(L2SceneBlock block) {
        return new MemoryL2Item(
                block.id(), block.sceneKey(), MemoryL2Content.strip(block.summary()), block.lastActiveAt());
    }

    private static MemoryL2Detail toDetail(L2SceneBlock block) {
        return new MemoryL2Detail(
                block.id(),
                block.sceneKey(),
                block.title(),
                MemoryL2Content.strip(block.summary()),
                block.lastActiveAt());
    }

    private static MemoryL3Content toContent(L3Persona persona) {
        return new MemoryL3Content(
                persona.id(), persona.personaText() == null ? "" : persona.personaText(), persona.lastRefreshedAt());
    }

    private static void validateCursor(long beforeId, int limit) {
        if (beforeId < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CODE_LAYER_INVALID, "分页游标不能为负数。");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_LAYER_INVALID, "分页数量必须在 1 到 200 之间。");
        }
    }

    private static String validateContent(@Nullable String content) {
        if (!StringUtils.hasText(content)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CODE_LAYER_INVALID, "content 不能为空。");
        }
        var normalized = content.strip();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CODE_LAYER_INVALID, "content 长度不能超过 200000。");
        }
        return normalized;
    }

    private static String validatePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CODE_LAYER_INVALID, "path 不能为空。");
        }
        var normalized = path.strip();
        if (normalized.length() > MAX_PATH_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CODE_LAYER_INVALID, "path 长度不能超过 128。");
        }
        return normalized;
    }

    private static @Nullable String normalizeQuery(@Nullable String query) {
        return StringUtils.hasText(query) ? query.strip() : null;
    }

    private static ApiException layerNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_LAYER_NOT_FOUND, "记忆层记录不存在。");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_LAYER_INVALID, message);
    }
}