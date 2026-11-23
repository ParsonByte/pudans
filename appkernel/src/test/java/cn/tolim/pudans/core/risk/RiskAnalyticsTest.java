package cn.tolim.pudans.core.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.contract.risk.dto.RiskHeatmapCell;
import cn.tolim.pudans.contract.risk.dto.RiskOverviewSummary;
import cn.tolim.pudans.contract.risk.dto.RiskRelation;
import cn.tolim.pudans.contract.risk.dto.RiskTimelinePoint;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.audit.AuditStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class RiskAnalyticsTest {

    private static final Instant NOW = Instant.parse("2026-11-23T04:00:00Z");

    @Mock
    AuditLog auditLog;

    @Mock
    RiskGuard riskGuard;

    @Mock
    RiskRuleRegistry ruleRegistry;

    private RiskAnalytics analytics;

    @BeforeEach
    void setUp() {
        analytics = new RiskAnalytics(auditLog, riskGuard, ruleRegistry);
    }

    private static RiskRule.Spec rule(String scene, String description, String action, boolean enabled) {
        var spec = new RiskRule.Spec();
        spec.setScene(scene);
        spec.setDescription(description);
        spec.setAction(action);
        spec.setEnabled(enabled);
        return spec;
    }

    private static AuditRecord event(
            String id,
            String timestamp,
            String operation,
            String result,
            String scene,
            String subject,
            String ip,
            String path,
            long count) {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName(id);
        record.setMetadata(metadata);
        record.setOperation(operation);
        record.setResult(result);
        record.setResourceName(scene);
        record.setIp(ip);
        record.setRequestPath(path);
        record.setTimestamp(Instant.parse(timestamp));
        var context = new LinkedHashMap<String, Object>();
        if (scene != null) {
            context.put("scene", scene);
        }
        if (subject != null) {
            context.put("subject", subject);
        }
        context.put("count", count);
        record.setContext(context);
        return record;
    }

    private void stubRecords(List<AuditRecord> blocked, List<AuditRecord> alerts) {
        when(auditLog.query(any(AuditStore.AuditQuery.class))).thenAnswer(invocation -> {
            var query = invocation.getArgument(0, AuditStore.AuditQuery.class);
            return switch (query.operation()) {
                case "RISK_BLOCKED" -> Flux.fromIterable(blocked);
                case "RISK_ALERT" -> Flux.fromIterable(alerts);
                default -> Flux.empty();
            };
        });
    }

    @Test
    void overviewAggregatesRealRiskEventsIntoChartsAndRelations() {
        stubRecords(
                List.of(
                        event(
                                "blocked-1",
                                "2026-11-18T03:47:59Z",
                                "RISK_BLOCKED",
                                "DENIED",
                                "request-flood",
                                "1.2.3.4",
                                "1.2.3.4",
                                "/sign-in",
                                5),
                        event(
                                "blocked-2",
                                "2026-11-18T03:48:59Z",
                                "RISK_BLOCKED",
                                "DENIED",
                                "login-failure",
                                "2.3.4.5",
                                "2.3.4.5",
                                "/login",
                                3),
                        event(
                                "blocked-3",
                                "2026-11-20T16:00:00Z",
                                "RISK_BLOCKED",
                                "DENIED",
                                "request-flood",
                                "1.2.3.4",
                                "1.2.3.4",
                                "/api",
                                6)),
                List.of(event(
                        "alert-1",
                        "2026-11-19T01:00:00Z",
                        "RISK_ALERT",
                        "FAILURE",
                        "auth-failure",
                        "2.3.4.5",
                        "2.3.4.5",
                        "/auth",
                        4)));
        when(riskGuard.listBans())
                .thenReturn(Flux.just(new RiskGuard.RiskBan("request-flood", "1.2.3.4", 45L)));
        var rules = Map.of(
                "request-flood", rule("request-flood", "同一 IP 请求过多。", "BLOCK", true),
                "login-failure", rule("login-failure", "同一 IP 连续登录失败。", "BLOCK", true),
                "auth-failure", rule("auth-failure", "同一 IP 认证失败。", "ALERT", true),
                "server-error", rule("server-error", "同一 IP 服务端错误。", "ALERT", false));
        when(ruleRegistry.rules()).thenReturn(rules);
        when(ruleRegistry.label(anyString()))
                .thenAnswer(invocation -> switch (invocation.getArgument(0, String.class)) {
                    case "request-flood" -> "请求洪泛";
                    case "login-failure" -> "连续登录失败";
                    case "auth-failure" -> "认证失败";
                    case "server-error" -> "服务端错误";
                    default -> null;
                });

        var overview = analytics.overview(7, NOW).block();

        assertThat(overview).isNotNull();
        assertThat(overview.rangeDays()).isEqualTo(7);
        assertThat(overview.generatedAt()).isEqualTo(NOW);
        assertThat(overview.truncated()).isFalse();
        assertThat(overview.summary())
                .isEqualTo(new RiskOverviewSummary(4, 3, 1, 1, 2, 3, 4));
        assertThat(overview.timeline()).hasSize(7);
        assertThat(overview.timeline().getFirst().date()).isEqualTo("2026-11-17");
        assertThat(overview.timeline()).anySatisfy(point -> {
            if ("2026-11-18".equals(point.date())) {
                assertThat(point).isEqualTo(new RiskTimelinePoint("2026-11-18", 2, 2, 0));
            }
        });
        assertThat(overview.timeline()).anySatisfy(point -> {
            if ("2026-11-19".equals(point.date())) {
                assertThat(point).isEqualTo(new RiskTimelinePoint("2026-11-19", 1, 0, 1));
            }
        });
        assertThat(overview.timeline()).anySatisfy(point -> {
            if ("2026-11-21".equals(point.date())) {
                assertThat(point).isEqualTo(new RiskTimelinePoint("2026-11-21", 1, 1, 0));
            }
        });
        assertThat(overview.heatmap()).hasSize(168);
        assertThat(overview.heatmap().stream().mapToLong(cell -> cell.events()).sum()).isEqualTo(4);
        assertThat(cell(overview.heatmap(), 3, 11).events()).isEqualTo(2);
        assertThat(cell(overview.heatmap(), 4, 9).events()).isEqualTo(1);
        assertThat(cell(overview.heatmap(), 6, 0).events()).isEqualTo(1);
        assertThat(overview.scenes()).hasSize(4);
        var flood = overview.scenes().stream()
                .filter(scene -> "request-flood".equals(scene.scene()))
                .findFirst()
                .orElseThrow();
        assertThat(flood.label()).isEqualTo("请求洪泛");
        assertThat(flood.action()).isEqualTo("BLOCK");
        assertThat(flood.events()).isEqualTo(2);
        assertThat(flood.blocked()).isEqualTo(2);
        assertThat(flood.subjects()).isEqualTo(1);
        var disabled = overview.scenes().stream()
                .filter(scene -> "server-error".equals(scene.scene()))
                .findFirst()
                .orElseThrow();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.events()).isZero();
        assertThat(overview.subjects()).hasSize(2);
        assertThat(overview.subjects().getFirst().subject()).isEqualTo("1.2.3.4");
        assertThat(overview.subjects().getFirst().events()).isEqualTo(2);
        assertThat(overview.subjects().getLast().scenes()).isEqualTo(2);
        assertThat(overview.relations())
                .contains(
                        new RiskRelation("request-flood", "1.2.3.4", 2, 2),
                        new RiskRelation("login-failure", "2.3.4.5", 1, 1),
                        new RiskRelation("auth-failure", "2.3.4.5", 1, 0));
        assertThat(overview.recentEvents()).hasSize(4);
        assertThat(overview.recentEvents().getFirst().auditId()).isEqualTo("blocked-3");
        assertThat(overview.recentEvents().getFirst().count()).isEqualTo(6);
    }

    @Test
    void overviewKeepsCompleteDailyAndHourlyBucketsWithoutEvents() {
        stubRecords(List.of(), List.of());
        when(riskGuard.listBans()).thenReturn(Flux.empty());
        when(ruleRegistry.rules()).thenReturn(Map.of());

        var overview = analytics.overview(7, NOW).block();

        assertThat(overview).isNotNull();
        assertThat(overview.summary().events()).isZero();
        assertThat(overview.summary().affectedSubjects()).isZero();
        assertThat(overview.timeline()).hasSize(7);
        assertThat(overview.timeline()).allSatisfy(point -> {
            assertThat(point.events()).isZero();
            assertThat(point.blocked()).isZero();
            assertThat(point.alerts()).isZero();
        });
        assertThat(overview.heatmap()).hasSize(168);
        assertThat(overview.heatmap()).allSatisfy(cell -> assertThat(cell.events()).isZero());
        assertThat(overview.scenes()).isEmpty();
        assertThat(overview.subjects()).isEmpty();
        assertThat(overview.relations()).isEmpty();
        assertThat(overview.recentEvents()).isEmpty();
    }

    @Test
    void overviewFallsBackToRecordFieldsWhenContextIsMissing() {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName("blocked-without-context");
        record.setMetadata(metadata);
        record.setOperation("RISK_BLOCKED");
        record.setResult("DENIED");
        record.setResourceName("server-error");
        record.setIp("9.9.9.9");
        record.setRequestPath("/api");
        record.setTimestamp(Instant.parse("2026-11-22T01:00:00Z"));
        stubRecords(List.of(record), List.of());
        when(riskGuard.listBans()).thenReturn(Flux.empty());
        when(ruleRegistry.rules()).thenReturn(Map.of());
        when(ruleRegistry.label(anyString())).thenReturn(null);

        var overview = analytics.overview(7, NOW).block();

        assertThat(overview).isNotNull();
        assertThat(overview.summary().events()).isEqualTo(1);
        assertThat(overview.summary().affectedSubjects()).isEqualTo(1);
        assertThat(overview.recentEvents().getFirst().scene()).isEqualTo("server-error");
        assertThat(overview.recentEvents().getFirst().subject()).isEqualTo("9.9.9.9");
        assertThat(overview.recentEvents().getFirst().count()).isZero();
        assertThat(overview.scenes().getFirst().label()).isEqualTo("server-error");
    }

    private static RiskHeatmapCell cell(List<RiskHeatmapCell> cells, int weekday, int hour) {
        return cells.stream()
                .filter(cell -> cell.weekday() == weekday && cell.hour() == hour)
                .findFirst()
                .orElseThrow();
    }
}
