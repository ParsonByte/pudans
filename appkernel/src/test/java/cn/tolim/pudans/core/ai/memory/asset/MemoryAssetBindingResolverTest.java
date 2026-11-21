package cn.tolim.pudans.core.ai.memory.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiMemoryAsset;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryAssetBindingResolverTest {

    private static final MemoryContext ORIGIN =
            new MemoryContext("team-1", "user-1", "agent-1", "session-1", null);

    @Mock
    ReactiveExtensionClient client;

    private MemoryAssetBindingResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MemoryAssetBindingResolver(client);
    }

    @Test
    void returnsActiveBindingsInCreationOrderAndSkipsOwnAndDuplicateScopes() {
        var own = asset(
                "own",
                "team-1",
                "user-1",
                "agent-1",
                List.of("agent-1"),
                AiMemoryAsset.Status.ACTIVE,
                Instant.parse("2026-11-19T00:00:00Z"));
        var later = asset(
                "later",
                "team-1",
                "user-9",
                "agent-9",
                List.of("agent-1"),
                AiMemoryAsset.Status.ACTIVE,
                Instant.parse("2026-11-19T02:00:00Z"));
        var disabled = asset(
                "disabled",
                "team-1",
                "user-3",
                "agent-3",
                List.of("agent-1"),
                AiMemoryAsset.Status.DISABLED,
                Instant.parse("2026-11-19T00:30:00Z"));
        var earlier = asset(
                "earlier",
                "team-1",
                "user-2",
                "agent-2",
                List.of("agent-1", "agent-4"),
                AiMemoryAsset.Status.ACTIVE,
                Instant.parse("2026-11-19T01:00:00Z"));
        var duplicate = asset(
                "duplicate",
                "team-1",
                "user-2",
                "agent-2",
                List.of("agent-1"),
                AiMemoryAsset.Status.ACTIVE,
                Instant.parse("2026-11-19T03:00:00Z"));
        var otherAgent = asset(
                "other",
                "team-1",
                "user-5",
                "agent-5",
                List.of("agent-9"),
                AiMemoryAsset.Status.ACTIVE,
                Instant.parse("2026-11-19T00:10:00Z"));
        when(client.listAll(eq(AiMemoryAsset.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(later, disabled, own, duplicate, otherAgent, earlier));

        StepVerifier.create(resolver.borrowedScopes(ORIGIN))
                .assertNext(scopes -> {
                    assertThat(scopes).hasSize(2);
                    assertThat(scopes.get(0).teamId()).isEqualTo("team-1");
                    assertThat(scopes.get(0).userId()).isEqualTo("user-2");
                    assertThat(scopes.get(0).agentId()).isEqualTo("agent-2");
                    assertThat(scopes.get(0).sessionId()).isEqualTo("session-1");
                    assertThat(scopes.get(1).teamId()).isEqualTo("team-1");
                    assertThat(scopes.get(1).agentId()).isEqualTo("agent-9");
                })
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenNoBindingTargetsAgent() {
        when(client.listAll(eq(AiMemoryAsset.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(resolver.borrowedScopes(ORIGIN))
                .assertNext(scopes -> assertThat(scopes).isEmpty())
                .verifyComplete();
    }

    private static AiMemoryAsset asset(
            String name,
            String team,
            String sourceUser,
            String sourceAgent,
            List<String> agents,
            AiMemoryAsset.Status status,
            Instant createdAt) {
        var spec = new AiMemoryAsset.Spec();
        spec.setDisplayName(name);
        spec.setTeam(team);
        spec.setOwnerUserId("owner-1");
        spec.setSourceUserId(sourceUser);
        spec.setSourceAgentId(sourceAgent);
        spec.setAgents(agents);
        spec.setStatus(status);
        var asset = new AiMemoryAsset();
        asset.setSpec(spec);
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setCreationTimestamp(createdAt);
        asset.setMetadata(metadata);
        return asset;
    }
}