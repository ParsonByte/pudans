package cn.tolim.pudans.core.risk.endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.risk.dto.RiskOverview;
import cn.tolim.pudans.contract.risk.dto.RiskOverviewSummary;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.risk.RiskAnalytics;
import cn.tolim.pudans.core.risk.RiskGuard;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class RiskRuleEndpointTest {

    @Mock
    RiskGuard riskGuard;

    @Mock
    AuditLog auditLog;

    @Mock
    RiskAnalytics riskAnalytics;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToRouterFunction(
                        new RiskRuleEndpoint(riskGuard, auditLog, riskAnalytics).endpoint())
                .build();
    }

    private static RiskOverview overview(int days) {
        return new RiskOverview(
                days,
                Instant.parse("2026-11-23T04:00:00Z"),
                false,
                new RiskOverviewSummary(0, 0, 0, 0, 0, 8, 8),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void overviewDefaultsToSevenDays() {
        when(riskAnalytics.overview(7)).thenReturn(Mono.just(overview(7)));

        webTestClient
                .get()
                .uri("/riskrules/overview")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.rangeDays")
                .isEqualTo(7);

        verify(riskAnalytics).overview(7);
    }

    @Test
    void overviewPassesAValidatedRangeToTheAggregator() {
        when(riskAnalytics.overview(30)).thenReturn(Mono.just(overview(30)));

        webTestClient
                .get()
                .uri("/riskrules/overview?days=30")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.rangeDays")
                .isEqualTo(30);

        verify(riskAnalytics).overview(30);
    }

    @Test
    void overviewRejectsInvalidRangesBeforeAggregation() {
        webTestClient
                .get()
                .uri("/riskrules/overview?days=0")
                .exchange()
                .expectStatus()
                .isBadRequest();
        webTestClient
                .get()
                .uri("/riskrules/overview?days=91")
                .exchange()
                .expectStatus()
                .isBadRequest();
        webTestClient
                .get()
                .uri("/riskrules/overview?days=week")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(riskAnalytics);
    }
}
