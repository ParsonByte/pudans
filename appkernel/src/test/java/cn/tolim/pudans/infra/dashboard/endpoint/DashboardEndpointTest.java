package cn.tolim.pudans.infra.dashboard.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.contract.SystemInfo;
import cn.tolim.pudans.infra.contract.ParameterInfoGetter;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.core.risk.RiskRuleRegistry;
import cn.tolim.pudans.infra.audit.AuditStore;
import cn.tolim.pudans.infra.utils.PudansTime;
import com.github.zafarkhaja.semver.Version;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DashboardEndpointTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AuditLog auditLog;

    @Mock
    ParameterInfoGetter systemInfoGetter;

    @Mock
    Groups groups;

    @Mock
    RiskRuleRegistry riskRuleRegistry;

    @Mock
    RiskGuard riskGuard;

    private DashboardEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new DashboardEndpoint(
                client, auditLog, systemInfoGetter, groups, riskRuleRegistry, riskGuard);
    }

    private static AuditRecord record(String id, String uid, String operation, String result) {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName(id);
        record.setMetadata(metadata);
        record.setUid(uid);
        record.setOperation(operation);
        record.setResult(result);
        record.setTimestamp(Instant.parse("2026-11-16T01:02:03Z"));
        return record;
    }

    private static User user(String name, String uid, String displayName) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(name);
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setUid(uid);
        spec.setDisplayName(displayName);
        user.setSpec(spec);
        return user;
    }

    private static UserGroup userGroup(String name) {
        var group = new UserGroup();
        var metadata = new Metadata();
        metadata.setName(name);
        group.setMetadata(metadata);
        return group;
    }

    private static RiskRule riskRule(String name, Instant creationTimestamp) {
        var riskRule = new RiskRule();
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setCreationTimestamp(creationTimestamp);
        riskRule.setMetadata(metadata);
        return riskRule;
    }

    private static Webhook webhook(String name, Instant creationTimestamp) {
        var webhook = new Webhook();
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setCreationTimestamp(creationTimestamp);
        webhook.setMetadata(metadata);
        return webhook;
    }

    private static Role role(String name, String displayName, Map<String, String> labels) {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setLabels(new java.util.HashMap<>(labels));
        metadata.setAnnotations(Map.of("rbac.authorization.pudans.tolim.cn/display-name", displayName));
        role.setMetadata(metadata);
        return role;
    }

    private static Role realRole(String name, String displayName) {
        return role(name, displayName, Map.of("pudans.tolim.cn/builtin", "true"));
    }

    private static Role templateRole(String name) {
        return role(name, name, Map.of("pudans.tolim.cn/role-template", "true"));
    }

    private static Role hiddenRole(String name) {
        return role(name, name, Map.of("pudans.tolim.cn/hidden", "true"));
    }

    private void stubRealRoles() {
        when(client.listAll(eq(Role.class), any(ListOptions.class), any()))
                .thenReturn(Flux.just(
                        realRole("super-role", "超级管理员"),
                        realRole("system-admin-role", "系统管理员"),
                        templateRole("role-template-manage-users"),
                        hiddenRole("hidden-auditor")));
    }

    @Test
    void aggregateCountsOnlyRealRolesAndDistributesUsersOnThem() throws Exception {
        var info = new SystemInfo()
                .setTitle("PUDANS")
                .setSubtitle("Console")
                .setUrl(new URL("http://localhost:8090"))
                .setVersion(Version.parse("1.0.0"));
        when(systemInfoGetter.get()).thenReturn(Mono.just(info));
        var alice = user("alice", "uid-1", "Alice");
        var bob = user("bob", "uid-2", "Bob");
        when(client.listAll(eq(User.class), any(ListOptions.class), any()))
                .thenReturn(Flux.just(alice, bob));
        stubRealRoles();
        when(client.countBy(eq(User.class), any(ListOptions.class))).thenReturn(Mono.just(2L));
        when(client.countBy(eq(UserGroup.class), any(ListOptions.class))).thenReturn(Mono.just(3L));
        when(riskRuleRegistry.ruleCount()).thenReturn(8);
        when(client.countBy(eq(Webhook.class), any(ListOptions.class))).thenReturn(Mono.just(2L));
        when(client.listAll(eq(RiskRule.class), any(ListOptions.class), any()))
                .thenReturn(Flux.just(riskRule("risk-rule-1", Instant.now())));
        when(client.listAll(eq(Webhook.class), any(ListOptions.class), any()))
                .thenReturn(Flux.just(webhook("webhook-1", Instant.now())));
        when(auditLog.countSince(any(Instant.class))).thenReturn(Mono.just(41L));
        when(auditLog.findRecent(5)).thenReturn(Flux.empty());
        when(auditLog.countPerDay(any(Instant.class), any(Instant.class), isNull(), eq("LOGIN"), eq("SUCCESS")))
                .thenReturn(Mono.just(Map.of(LocalDate.now(PudansTime.BEIJING), 7L)));
        when(auditLog.countPerDay(any(Instant.class), any(Instant.class), isNull(), isNull(), isNull()))
                .thenReturn(Mono.just(Map.of(LocalDate.now(PudansTime.BEIJING), 12L)));
        when(auditLog.countPerDay(any(Instant.class), any(Instant.class), isNull(), isNull(), eq("FAILURE")))
                .thenReturn(Mono.just(Map.of(LocalDate.now(PudansTime.BEIJING), 2L)));
        when(auditLog.countPerDay(any(Instant.class), any(Instant.class), isNull(), isNull(), eq("DENIED")))
                .thenReturn(Mono.just(Map.of(LocalDate.now(PudansTime.BEIJING), 1L)));
        when(auditLog.query(any(AuditStore.AuditQuery.class)))
                .thenReturn(Flux.just(
                        record("audit-1", "uid-1", "LOGIN", "SUCCESS"),
                        record("audit-2", "uid-1", "LOGIN", "SUCCESS"),
                        record("audit-3", "uid-2", "LOGIN", "SUCCESS")));
        when(auditLog.countByResult(any(Instant.class), any(Instant.class), isNull()))
                .thenReturn(Mono.just(Map.of("SUCCESS", 30L, "FAILURE", 8L, "DENIED", 3L)));
        when(auditLog.countByResult(any(Instant.class), any(Instant.class), eq("LOGIN")))
                .thenReturn(Mono.just(Map.of("SUCCESS", 20L, "FAILURE", 4L, "DENIED", 1L)));
        when(auditLog.countDistinctUids(any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(2L));
        when(auditLog.topOperations(any(Instant.class), any(Instant.class), eq(6)))
                .thenReturn(Mono.just(Map.of("NAVIGATION", 12L, "LOGIN", 9L)));
        when(riskGuard.listBans())
                .thenReturn(Flux.just(new RiskGuard.RiskBan("login-failure", "127.0.0.1", 60L)));
        when(groups.listMemberships())
                .thenReturn(Flux.just(
                        new Groups.Membership("alice", "admins"),
                        new Groups.Membership("bob", "operators")));
        when(groups.listUserGroups(anyCollection()))
                .thenReturn(Flux.just(userGroup("admins"), userGroup("operators")));
        when(groups.listRoleNamesOfGroup(any()))
                .thenAnswer(invocation -> "admins"
                                .equals(invocation.getArgument(0, UserGroup.class)
                                        .getMetadata()
                                        .getName())
                        ? Flux.just("super-role")
                        : Flux.just("system-admin-role", "role-template-manage-users"));

        var overview = endpoint.aggregate().block();

        assertThat(overview).isNotNull();
        assertThat(overview.globalInfo().siteTitle()).isEqualTo("PUDANS");
        assertThat(overview.globalInfo().version()).isEqualTo("1.0.0");
        assertThat(overview.totalUsers()).isEqualTo(2L);
        assertThat(overview.totalRoles()).isEqualTo(2L);
        assertThat(overview.totalUserGroups()).isEqualTo(3L);
        assertThat(overview.totalRiskRules()).isEqualTo(8L);
        assertThat(overview.totalWebhooks()).isEqualTo(2L);
        assertThat(overview.todayOperations()).isEqualTo(41L);
        assertThat(overview.loginTrend()).hasSize(7);
        assertThat(overview.loginTrend().getLast().count()).isEqualTo(7L);
        assertThat(overview.operationTrend()).hasSize(7);
        assertThat(overview.operationTrend().getLast().count()).isEqualTo(12L);
        assertThat(overview.riskRuleTrend()).hasSize(7);
        assertThat(overview.riskRuleTrend().getLast().count()).isEqualTo(1L);
        assertThat(overview.webhookTrend()).hasSize(7);
        assertThat(overview.webhookTrend().getLast().count()).isEqualTo(1L);
        assertThat(overview.activeUsers()).hasSize(2);
        assertThat(overview.activeUsers().getFirst().name()).isEqualTo("Alice");
        assertThat(overview.activeUsers().getFirst().operations()).isEqualTo(2L);
        assertThat(overview.activeUsers().getLast().name()).isEqualTo("Bob");
        assertThat(overview.roleDistribution())
                .extracting(item -> item.role())
                .containsExactlyInAnyOrder("super-role", "system-admin-role");
        assertThat(overview.roleDistribution())
                .extracting(item -> item.displayName())
                .containsExactlyInAnyOrder("超级管理员", "系统管理员");
        assertThat(overview.rangeDays()).isEqualTo(7);
        assertThat(overview.failureTrend()).hasSize(7);
        assertThat(overview.resultDistribution()).hasSize(3);
        assertThat(overview.topOperations())
                .extracting(item -> item.operation())
                .containsExactlyInAnyOrder("NAVIGATION", "LOGIN");
        assertThat(overview.metrics()).hasSize(8);
        assertThat(overview.metrics())
                .filteredOn(metric -> "loginSuccessRate".equals(metric.key()))
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.value()).isEqualTo(80.0);
                    assertThat(metric.previousValue()).isEqualTo(80.0);
                });
        assertThat(overview.insights()).isNotEmpty();

        var monthly = endpoint.aggregate(30).block();

        assertThat(monthly).isNotNull();
        assertThat(monthly.rangeDays()).isEqualTo(30);
        assertThat(monthly.loginTrend()).hasSize(30);

        var historical = endpoint.aggregate(7, Instant.parse("2026-11-20T12:00:00Z")).block();

        assertThat(historical).isNotNull();
        assertThat(historical.loginTrend()).hasSize(7);
        assertThat(historical.loginTrend().getLast().date()).isEqualTo("2026-11-20");
        assertThat(historical.operationTrend().getLast().date()).isEqualTo("2026-11-20");
    }

    @Test
    void aggregateSurvivesLookupFailuresWithSafeDefaults() {
        when(systemInfoGetter.get()).thenReturn(Mono.error(new IllegalStateException("down")));
        when(client.listAll(eq(Role.class), any(ListOptions.class), any())).thenReturn(Flux.empty());
        when(client.listAll(eq(RiskRule.class), any(ListOptions.class), any()))
                .thenReturn(Flux.error(new IllegalStateException("down")));
        when(client.listAll(eq(Webhook.class), any(ListOptions.class), any()))
                .thenReturn(Flux.error(new IllegalStateException("down")));
        when(client.countBy(any(Class.class), any(ListOptions.class)))
                .thenReturn(Mono.error(new IllegalStateException("down")));
        when(auditLog.countSince(any(Instant.class))).thenReturn(Mono.error(new IllegalStateException("down")));
        when(auditLog.findRecent(5)).thenReturn(Flux.error(new IllegalStateException("down")));
        when(auditLog.countPerDay(any(Instant.class), any(Instant.class), isNull(), eq("LOGIN"), eq("SUCCESS")))
                .thenReturn(Mono.error(new IllegalStateException("down")));
        when(auditLog.countPerDay(any(Instant.class), any(Instant.class), isNull(), isNull(), isNull()))
                .thenReturn(Mono.error(new IllegalStateException("down")));
        when(auditLog.countPerDay(any(Instant.class), any(Instant.class), isNull(), isNull(), eq("FAILURE")))
                .thenReturn(Mono.error(new IllegalStateException("down")));
        when(auditLog.countPerDay(any(Instant.class), any(Instant.class), isNull(), isNull(), eq("DENIED")))
                .thenReturn(Mono.error(new IllegalStateException("down")));
        when(auditLog.query(any(AuditStore.AuditQuery.class)))
                .thenReturn(Flux.error(new IllegalStateException("down")));
        when(auditLog.countByResult(any(Instant.class), any(Instant.class), any()))
                .thenReturn(Mono.error(new IllegalStateException("down")));
        when(auditLog.countDistinctUids(any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.error(new IllegalStateException("down")));
        when(auditLog.topOperations(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(Mono.error(new IllegalStateException("down")));
        when(riskGuard.listBans()).thenReturn(Flux.error(new IllegalStateException("down")));
        when(groups.listMemberships()).thenReturn(Flux.error(new IllegalStateException("down")));

        var overview = endpoint.aggregate().block();

        assertThat(overview).isNotNull();
        assertThat(overview.globalInfo().siteTitle()).isNull();
        assertThat(overview.totalUsers()).isZero();
        assertThat(overview.totalRoles()).isZero();
        assertThat(overview.todayOperations()).isZero();
        assertThat(overview.recentAuditLogs()).isEmpty();
        assertThat(overview.loginTrend()).isEmpty();
        assertThat(overview.operationTrend()).isEmpty();
        assertThat(overview.failureTrend()).isEmpty();
        assertThat(overview.riskRuleTrend()).isEmpty();
        assertThat(overview.webhookTrend()).isEmpty();
        assertThat(overview.activeUsers()).isEmpty();
        assertThat(overview.roleDistribution()).isEmpty();
        assertThat(overview.resultDistribution()).isEmpty();
        assertThat(overview.topOperations()).isEmpty();
        assertThat(overview.insights()).hasSize(1);
    }
}
