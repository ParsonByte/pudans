package cn.tolim.pudans.core.ai.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiTeam;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiTeamQuotaServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiStatsRepository statsRepository;

    @Test
    void rejectsARequestThatExceedsTheTeamWindowQuota() {
        var team = team(new BigDecimal("100"));
        when(client.get(AiTeam.class, "team-1")).thenReturn(Mono.just(team));
        when(statsRepository.teamUnitsUsed(any(), any())).thenReturn(Mono.just(new BigDecimal("90")));
        var service = new AiTeamQuotaService(client, statsRepository);

        StepVerifier.create(service.check("team-1", new BigDecimal("20")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .hasMessageContaining("团队额度"))
                .verify();

        verify(statsRepository).teamUnitsUsed(eq("team-1"), any());
    }

    @Test
    void allowsTeamsWithoutAQuota() {
        var team = team(null);
        when(client.get(AiTeam.class, "team-1")).thenReturn(Mono.just(team));
        var service = new AiTeamQuotaService(client, statsRepository);

        StepVerifier.create(service.check("team-1", BigDecimal.TEN)).verifyComplete();
    }

    private static AiTeam team(BigDecimal limit) {
        var team = new AiTeam();
        var metadata = new Metadata();
        metadata.setName("team-1");
        team.setMetadata(metadata);
        team.getSpec().setDisplayName("Team");
        team.getSpec().setOwnerUserId("owner");
        team.getSpec().getQuota().setLimit(limit);
        return team;
    }
}