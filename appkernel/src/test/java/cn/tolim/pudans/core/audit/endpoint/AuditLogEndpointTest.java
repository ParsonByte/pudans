package cn.tolim.pudans.core.audit.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.audit.service.AuditService;
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
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AuditLogEndpointTest {

    @Mock
    AuditService auditService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToRouterFunction(new AuditLogEndpoint(auditService).endpoint())
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
        when(auditService.page(any(), eq(1000), eq(10)))
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
        var records = List.of(
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
                        0));

        var result = AuditLogEndpoint.toJourneyPage(records, 1, 1);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).uid()).isEqualTo("u2");
        assertThat(result.pageViews()).isEqualTo(2);
        assertThat(result.actionCount()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
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
    void summaryJourneyPageOmitsStepsButKeepsAggregateCounts() {
        var base = Instant.parse("2026-11-18T01:00:00Z");
        var records = List.of(
                page("page-a", "u1", base.plusSeconds(1), "/console/roles"),
                record(
                        "read-a",
                        "u1",
                        "READ",
                        "User",
                        "alice",
                        "/apis/api.pudans.tolim.cn/v1alpha1/users/alice",
                        base.plusSeconds(2),
                        0));

        var result = AuditLogEndpoint.toJourneyPage(records, 1, 10, false);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).steps()).isEmpty();
        assertThat(result.items().get(0).pageCount()).isEqualTo(1);
        assertThat(result.items().get(0).actionCount()).isEqualTo(1);
        assertThat(result.pageViews()).isEqualTo(1);
        assertThat(result.actionCount()).isEqualTo(1);
    }
}
