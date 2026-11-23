package cn.tolim.pudans.core.audit.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.contract.audit.dto.AuditFlowOverview;
import cn.tolim.pudans.contract.audit.dto.AuditFlowOverviewSummary;
import cn.tolim.pudans.contract.audit.dto.AuditOverview;
import cn.tolim.pudans.contract.audit.dto.AuditOverviewSummary;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.core.audit.AuditAnalytics;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.audit.AuditStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AuditLogEndpointTest {

    @Mock
    AuditLog auditLog;

    @Mock
    AuditAnalytics auditAnalytics;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToRouterFunction(
                        new AuditLogEndpoint(auditLog, auditAnalytics).endpoint())
                .build();
    }

    private static AuditRecord record(
            String id,
            String uid,
            String operation,
            String resourceType,
            String resourceName,
            String path,
            Instant timestamp,
            long durationMs) {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName(id);
        record.setMetadata(metadata);
        record.setUid(uid);
        record.setOperation(operation);
        record.setResourceType(resourceType);
        record.setResourceName(resourceName);
        record.setRequestMethod("GET");
        record.setRequestPath(path);
        record.setResult("SUCCESS");
        record.setTimestamp(timestamp);
        record.setDurationMs(durationMs);
        record.setStepIndex(1);
        var context = new LinkedHashMap<String, Object>();
        context.put("username", "alice");
        record.setContext(context);
        return record;
    }

    private static AuditRecord page(String id, String uid, Instant timestamp, String path) {
        return record(id, uid, "NAVIGATION", "Page", path, path, timestamp, 5);
    }

    // 每两条记录一条页面、一条动作，页面路径各不相同以免被同页去重合并。
    private static List<AuditRecord> journeyScanRecords(int count) {
        var start = Instant.parse("2026-11-16T01:00:00Z");
        var records = new java.util.ArrayList<AuditRecord>(count);
        for (var index = 0; index < count; index++) {
            var uid = index % 2 == 0 ? "u1" : "u2";
            var timestamp = start.plusSeconds(index);
            records.add(index % 2 == 0
                    ? page("page-" + index, uid, timestamp, "/p/" + index)
                    : record("action-" + index, uid, "READ", "User", "u-" + index, "/p/" + index, timestamp, 5));
        }
        return records;
    }

    private static AuditOverview overview(int days) {
        return new AuditOverview(
                days,
                Instant.parse("2026-11-23T04:00:00Z"),
                new AuditOverviewSummary(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static AuditFlowOverview flowOverview(int days) {
        return new AuditFlowOverview(
                days,
                Instant.parse("2026-11-23T04:00:00Z"),
                new AuditFlowOverviewSummary(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void overviewDefaultsToSevenDays() {
        when(auditAnalytics.overview(7)).thenReturn(Mono.just(overview(7)));

        webTestClient
                .get()
                .uri("/auditlogs/overview")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.rangeDays")
                .isEqualTo(7);

        verify(auditAnalytics).overview(7);
    }

    @Test
    void overviewPassesAValidatedRangeToTheAggregator() {
        when(auditAnalytics.overview(30)).thenReturn(Mono.just(overview(30)));

        webTestClient
                .get()
                .uri("/auditlogs/overview?days=30")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.rangeDays")
                .isEqualTo(30);

        verify(auditAnalytics).overview(30);
    }

    @Test
    void overviewRejectsInvalidRangesBeforeAggregation() {
        webTestClient
                .get()
                .uri("/auditlogs/overview?days=0")
                .exchange()
                .expectStatus()
                .isBadRequest();
        webTestClient
                .get()
                .uri("/auditlogs/overview?days=91")
                .exchange()
                .expectStatus()
                .isBadRequest();
        webTestClient
                .get()
                .uri("/auditlogs/overview?days=week")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(auditAnalytics);
    }

    @Test
    void flowOverviewDefaultsToSevenDays() {
        when(auditAnalytics.flowOverview(7)).thenReturn(Mono.just(flowOverview(7)));

        webTestClient
                .get()
                .uri("/auditlogs/flows/overview")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.rangeDays")
                .isEqualTo(7);

        verify(auditAnalytics).flowOverview(7);
    }

    @Test
    void flowOverviewPassesAValidatedRangeToTheAggregator() {
        when(auditAnalytics.flowOverview(30)).thenReturn(Mono.just(flowOverview(30)));

        webTestClient
                .get()
                .uri("/auditlogs/flows/overview?days=30")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.rangeDays")
                .isEqualTo(30);

        verify(auditAnalytics).flowOverview(30);
    }

    @Test
    void flowOverviewRejectsInvalidRangesBeforeAggregation() {
        webTestClient
                .get()
                .uri("/auditlogs/flows/overview?days=0")
                .exchange()
                .expectStatus()
                .isBadRequest();
        webTestClient
                .get()
                .uri("/auditlogs/flows/overview?days=91")
                .exchange()
                .expectStatus()
                .isBadRequest();
        webTestClient
                .get()
                .uri("/auditlogs/flows/overview?days=week")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(auditAnalytics);
    }

    @Test
    void pageRejectsAPaginationDepthBeyondTheStoreLimit() {
        webTestClient
                .get()
                .uri("/auditlogs?page=1001&size=10")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void pageAllowsTheDeepestSupportedPage() {
        when(auditLog.page(any(), eq(1000), eq(10)))
                .thenReturn(Mono.just(new AuditStore.AuditPageResult(List.of(), 0L)));

        webTestClient
                .get()
                .uri("/auditlogs?page=1000&size=10")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void journeyLinksPagesToTheirActionsAndKeepsTheEntryAndExit() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        var records = List.of(
                record(
                        "login",
                        "u1",
                        "LOGIN",
                        "login",
                        "session",
                        "/apis/api.pudans.tolim.cn/v1alpha1/login/session",
                        base.plusMillis(10),
                        5),
                page("page-roles", "u1", base.plusSeconds(1).plusMillis(5), "/console/roles"),
                record(
                        "read-user",
                        "u1",
                        "READ",
                        "User",
                        "alice",
                        "/apis/api.pudans.tolim.cn/v1alpha1/users/alice",
                        base.plusSeconds(2),
                        8),
                page("page-users", "u1", base.plusSeconds(3).plusMillis(5), "/console/users"),
                record(
                        "update-user",
                        "u1",
                        "UPDATE",
                        "User",
                        "bob",
                        "/apis/api.pudans.tolim.cn/v1alpha1/users/bob",
                        base.plusSeconds(4),
                        12));

        var journey = AuditLogEndpoint.toJourney("u1", records);

        assertThat(journey.uid()).isEqualTo("u1");
        assertThat(journey.username()).isEqualTo("alice");
        assertThat(journey.entryPath()).isEqualTo("/console/roles");
        assertThat(journey.exitPath()).isEqualTo("/console/users");
        assertThat(journey.pageCount()).isEqualTo(2);
        assertThat(journey.actionCount()).isEqualTo(3);
        assertThat(journey.complete()).isTrue();
        assertThat(journey.sessionCount()).isEqualTo(1);
        assertThat(journey.resourceTypes()).contains("Page", "login", "User");
        assertThat(journey.operations()).contains("LOGIN", "NAVIGATION", "READ", "UPDATE");
        assertThat(journey.steps()).hasSize(3);

        var session = journey.steps().get(0);
        assertThat(session.kind()).isEqualTo("SESSION");
        assertThat(session.page()).isNull();
        assertThat(session.toPath()).isEqualTo("/console/roles");
        assertThat(session.actions()).extracting(record -> record.getMetadata().getName())
                .containsExactly("login");

        var roles = journey.steps().get(1);
        assertThat(roles.kind()).isEqualTo("PAGE");
        assertThat(roles.page().getRequestPath()).isEqualTo("/console/roles");
        assertThat(roles.fromPath()).isNull();
        assertThat(roles.toPath()).isEqualTo("/console/users");
        assertThat(roles.actions()).extracting(record -> record.getMetadata().getName())
                .containsExactly("read-user");

        var users = journey.steps().get(2);
        assertThat(users.toPath()).isNull();
        assertThat(users.actions()).extracting(record -> record.getMetadata().getName())
                .containsExactly("update-user");
    }

    @Test
    void duplicateClientNavigationWithinTenSecondsIsMergedIntoThePageStep() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        var records = List.of(
                page("page-server", "u1", base.plusSeconds(1).plusMillis(5), "/console/roles"),
                record(
                        "page-beacon",
                        "u1",
                        "NAVIGATION",
                        "Page",
                        "/console/roles",
                        "/apis/api.pudans.tolim.cn/v1alpha1/usernavigations",
                        base.plusSeconds(1).plusMillis(500),
                        5),
                record(
                        "read-user",
                        "u1",
                        "READ",
                        "User",
                        "alice",
                        "/apis/api.pudans.tolim.cn/v1alpha1/users/alice",
                        base.plusSeconds(2),
                        0));

        var journey = AuditLogEndpoint.toJourney("u1", records);

        assertThat(journey.pageCount()).isEqualTo(1);
        assertThat(journey.entryPath()).isEqualTo("/console/roles");
        assertThat(journey.exitPath()).isEqualTo("/console/roles");
        assertThat(journey.actionCount()).isEqualTo(1);
        assertThat(journey.steps()).hasSize(1);
        assertThat(journey.steps().get(0).actions()).extracting(record -> record.getMetadata().getName())
                .containsExactly("read-user");
    }

    @Test
    void clientNavigationUsesResourceNameAsThePagePath() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        var records = List.of(record(
                "page-beacon",
                "u1",
                "NAVIGATION",
                "Page",
                "/console/risk",
                "/apis/api.pudans.tolim.cn/v1alpha1/usernavigations",
                base,
                5));

        var journey = AuditLogEndpoint.toJourney("u1", records);

        assertThat(journey.entryPath()).isEqualTo("/console/risk");
        assertThat(journey.exitPath()).isEqualTo("/console/risk");
        assertThat(journey.pageCount()).isEqualTo(1);
        assertThat(journey.actionCount()).isZero();
        assertThat(journey.complete()).isTrue();
    }

    @Test
    void journeyPagePaginatesUsersAndReturnsAggregateCounts() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        when(auditLog.query(any()))
                .thenReturn(Flux.just(
                        page("page-a", "u1", base.plusSeconds(1), "/console/roles"),
                        record(
                                "read-a",
                                "u1",
                                "READ",
                                "User",
                                "alice",
                                "/apis/api.pudans.tolim.cn/v1alpha1/users/alice",
                                base.plusSeconds(2),
                                0),
                        page("page-b", "u2", base.plusSeconds(10), "/console/users"),
                        record(
                                "read-b",
                                "u2",
                                "READ",
                                "Role",
                                "admin",
                                "/apis/api.pudans.tolim.cn/v1alpha1/roles/admin",
                                base.plusSeconds(11),
                                0)));

        webTestClient
                .get()
                .uri("/auditlogs/journeys?page=1&size=1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.total")
                .isEqualTo(2)
                .jsonPath("$.items.length()")
                .isEqualTo(1)
                .jsonPath("$.items[0].uid")
                .isEqualTo("u2")
                .jsonPath("$.items[0].steps.length()")
                .isEqualTo(1)
                .jsonPath("$.pageViews")
                .isEqualTo(2)
                .jsonPath("$.actionCount")
                .isEqualTo(2)
                .jsonPath("$.truncated")
                .isEqualTo(false);

        verify(auditLog, times(2)).query(any());
    }

    @Test
    void journeyStatusSeparatesPageFailuresFromActionAnomalies() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        var page = page("page-a", "u1", base.plusSeconds(1), "/console/roles");
        var action = record(
                "read-a",
                "u1",
                "READ",
                "User",
                "alice",
                "/apis/api.pudans.tolim.cn/v1alpha1/users/alice",
                base.plusSeconds(2),
                0);

        action.setResult("FAILURE");
        assertThat(AuditLogEndpoint.toJourney("u1", List.of(page, action)).status())
                .isEqualTo("FAILURE");

        var failedPage = page("page-b", "u1", base.plusSeconds(3), "/console/users");
        failedPage.setResult("FAILURE");
        assertThat(AuditLogEndpoint.toJourney("u1", List.of(failedPage)).status())
                .isEqualTo("FAILED");

        action.setResult("DENIED");
        assertThat(AuditLogEndpoint.toJourney("u1", List.of(page, action)).status())
                .isEqualTo("DENIED");
    }

    @Test
    void sessionSummariesKeepTheWholeUserJourneyAndSplitAfterThirtyMinutes() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        var records = List.of(
                page("page-roles", "u1", base.plusSeconds(1), "/console/roles"),
                record(
                        "read-user",
                        "u1",
                        "READ",
                        "User",
                        "alice",
                        "/apis/api.pudans.tolim.cn/v1alpha1/users/alice",
                        base.plusSeconds(2),
                        8),
                page("page-users", "u1", base.plusSeconds(3), "/console/users"),
                page("page-settings", "u2", base.plusSeconds(4), "/console/settings"),
                page("page-risk", "u1", base.plusSeconds(31 * 60), "/console/risk"));

        var sessions = AuditLogEndpoint.sessionSummaries(records);

        assertThat(sessions).hasSize(3);
        assertThat(sessions.get(0).uid()).isEqualTo("u1");
        assertThat(sessions.get(0).entryPath()).isEqualTo("/console/risk");
        assertThat(sessions.get(0).exitPath()).isEqualTo("/console/risk");
        assertThat(sessions.get(1).uid()).isEqualTo("u2");
        assertThat(sessions.get(1).entryPath()).isEqualTo("/console/settings");
        assertThat(sessions.get(2).uid()).isEqualTo("u1");
        assertThat(sessions.get(2).entryPath()).isEqualTo("/console/roles");
        assertThat(sessions.get(2).exitPath()).isEqualTo("/console/users");
        assertThat(sessions.get(2).pageCount()).isEqualTo(2);
        assertThat(sessions.get(2).actionCount()).isEqualTo(1);
        assertThat(sessions.get(2).recordCount()).isEqualTo(3);
        assertThat(sessions.get(2).sessionId())
                .isEqualTo("u1:" + sessions.get(2).start().toEpochMilli());
    }

    @Test
    void sessionListFiltersByTheAggregatedSessionOutcome() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        var deniedPage = page("page-denied", "u1", base.plusSeconds(1), "/console/roles");
        deniedPage.setResult("DENIED");
        when(auditLog.query(any()))
                .thenReturn(Flux.just(
                        page("page-success", "u2", base.plusSeconds(2), "/console/users"),
                        deniedPage,
                        record(
                                "read-user",
                                "u1",
                                "READ",
                                "User",
                                "alice",
                                "/apis/api.pudans.tolim.cn/v1alpha1/users/alice",
                                base.plusSeconds(3),
                                5)));

        webTestClient
                .get()
                .uri("/auditlogs/sessions?result=DENIED&page=1&size=10")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.total")
                .isEqualTo(1)
                .jsonPath("$.items.length()")
                .isEqualTo(1)
                .jsonPath("$.items[0].uid")
                .isEqualTo("u1")
                .jsonPath("$.items[0].status")
                .isEqualTo("DENIED")
                .jsonPath("$.items[0].entryPath")
                .isEqualTo("/console/roles")
                .jsonPath("$.failedSessions")
                .isEqualTo(1);
    }

    @Test
    void sessionReplayReturnsEveryStepWithEntryAndExit() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        var records = List.of(
                page("page-roles", "u1", base.plusSeconds(1), "/console/roles"),
                record(
                        "read-user",
                        "u1",
                        "READ",
                        "User",
                        "alice",
                        "/apis/api.pudans.tolim.cn/v1alpha1/users/alice",
                        base.plusSeconds(2),
                        8),
                page("page-users", "u1", base.plusSeconds(3), "/console/users"));
        var session = AuditLogEndpoint.sessionSummaries(records).get(0);
        when(auditLog.query(any())).thenReturn(Flux.fromIterable(records));

        webTestClient
                .get()
                .uri("/auditlogs/sessions/{sessionId}", session.sessionId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.session.sessionId")
                .isEqualTo(session.sessionId())
                .jsonPath("$.session.entryPath")
                .isEqualTo("/console/roles")
                .jsonPath("$.session.exitPath")
                .isEqualTo("/console/users")
                .jsonPath("$.session.pageCount")
                .isEqualTo(2)
                .jsonPath("$.session.actionCount")
                .isEqualTo(1)
                .jsonPath("$.steps.length()")
                .isEqualTo(3)
                .jsonPath("$.steps[0].resourceName")
                .isEqualTo("/console/roles")
                .jsonPath("$.steps[1].operation")
                .isEqualTo("READ")
                .jsonPath("$.steps[2].resourceName")
                .isEqualTo("/console/users");
    }

    @Test
    void sessionReplayRejectsAnInvalidSessionId() {
        webTestClient
                .get()
                .uri("/auditlogs/sessions/not-a-session")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(auditLog);
    }

    @Test
    void sessionReplayAcceptsAStableSessionIdAndALegacyEndBoundId() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        var records = List.of(
                page("page-roles", "u1", base.plusSeconds(1), "/console/roles"),
                page("page-users", "u1", base.plusSeconds(3), "/console/users"));
        var session = AuditLogEndpoint.sessionSummaries(records).get(0);
        when(auditLog.query(any())).thenReturn(Flux.fromIterable(records));

        webTestClient
                .get()
                .uri("/auditlogs/sessions/{sessionId}", session.sessionId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.session.sessionId")
                .isEqualTo(session.sessionId());

        webTestClient
                .get()
                .uri("/auditlogs/sessions/{sessionId}", session.sessionId() + ":" + session.end().toEpochMilli())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.session.sessionId")
                .isEqualTo(session.sessionId())
                .jsonPath("$.steps.length()")
                .isEqualTo(2);
    }

    @Test
    void sessionReplaySupportsZeroDurationRecords() {
        var timestamp = Instant.parse("2026-11-18T01:00:00Z");
        var zeroDurationPage = record(
                "page-zero",
                "u1",
                "NAVIGATION",
                "Page",
                "/console/roles",
                "/console/roles",
                timestamp,
                0);
        var session = AuditLogEndpoint.sessionSummaries(List.of(zeroDurationPage)).get(0);
        when(auditLog.query(any())).thenReturn(Flux.just(zeroDurationPage));

        webTestClient
                .get()
                .uri("/auditlogs/sessions/{sessionId}", session.sessionId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.session.sessionId")
                .isEqualTo(session.sessionId())
                .jsonPath("$.steps.length()")
                .isEqualTo(1);
    }

    @Test
    void summaryJourneyPageOmitsStepsButKeepsAggregateCounts() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        when(auditLog.query(any()))
                .thenReturn(Flux.just(
                        page("page-a", "u1", base.plusSeconds(1), "/console/roles"),
                        record(
                                "read-a",
                                "u1",
                                "READ",
                                "User",
                                "alice",
                                "/apis/api.pudans.tolim.cn/v1alpha1/users/alice",
                                base.plusSeconds(2),
                                0)));

        webTestClient
                .get()
                .uri("/auditlogs/journeys?page=1&size=10&summary=true")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items.length()")
                .isEqualTo(1)
                .jsonPath("$.items[0].steps.length()")
                .isEqualTo(0)
                .jsonPath("$.items[0].pageCount")
                .isEqualTo(1)
                .jsonPath("$.items[0].actionCount")
                .isEqualTo(1)
                .jsonPath("$.pageViews")
                .isEqualTo(1)
                .jsonPath("$.actionCount")
                .isEqualTo(1);

        verify(auditLog, times(1)).query(any());
    }

    @Test
    void journeysStreamsTheWholeScanLimitWithoutReportingTruncation() {
        when(auditLog.query(any())).thenReturn(Flux.fromIterable(journeyScanRecords(20_000)));

        webTestClient
                .get()
                .uri("/auditlogs/journeys?page=1&size=1&summary=true")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.total")
                .isEqualTo(2)
                .jsonPath("$.truncated")
                .isEqualTo(false)
                .jsonPath("$.pageViews")
                .isEqualTo(10_000)
                .jsonPath("$.actionCount")
                .isEqualTo(10_000)
                .jsonPath("$.items.length()")
                .isEqualTo(1);
    }

    @Test
    void journeysReportsTruncationWhenTheScanLimitIsExceeded() {
        when(auditLog.query(any())).thenReturn(Flux.fromIterable(journeyScanRecords(20_001)));

        webTestClient
                .get()
                .uri("/auditlogs/journeys?page=1&size=1&summary=true")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.truncated")
                .isEqualTo(true)
                .jsonPath("$.pageViews")
                .isEqualTo(10_000)
                .jsonPath("$.actionCount")
                .isEqualTo(10_000);
    }
}
