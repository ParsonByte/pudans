package cn.tolim.pudans.core.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.infra.setting.ParameterGroups;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AuditRecordRuleEvaluatorTest {

    @Mock
    ParameterGroups parameterGroups;

    private AuditRecordRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AuditRecordRuleEvaluator(parameterGroups);
    }

    @Test
    void emptyRulesRecordEverything() {
        when(parameterGroups.getAudit()).thenReturn(Mono.just(new ParameterSetting.Audit()));

        assertThat(evaluator.shouldRecord("LOGIN", "User").block()).isTrue();
    }

    @Test
    void whitelistRecordsOnlyMatchingEntries() {
        var rules = new ParameterSetting.AuditRecordRules();
        rules.setMode("WHITELIST");
        rules.setOperations(List.of("LOGIN"));
        rules.setResourceTypes(List.of("User"));
        var audit = new ParameterSetting.Audit();
        audit.setAuditRecordRules(rules);
        when(parameterGroups.getAudit()).thenReturn(Mono.just(audit));

        assertThat(evaluator.shouldRecord("LOGIN", "Session").block()).isTrue();
        assertThat(evaluator.shouldRecord("READ", "User").block()).isTrue();
        assertThat(evaluator.shouldRecord("READ", "Session").block()).isFalse();
    }

    @Test
    void blacklistSkipsMatchingEntries() {
        var rules = new ParameterSetting.AuditRecordRules();
        rules.setMode("BLACKLIST");
        rules.setOperations(List.of("READ"));
        var audit = new ParameterSetting.Audit();
        audit.setAuditRecordRules(rules);
        when(parameterGroups.getAudit()).thenReturn(Mono.just(audit));

        assertThat(evaluator.shouldRecord("READ", "User").block()).isFalse();
        assertThat(evaluator.shouldRecord("LOGIN", "User").block()).isTrue();
    }

    @Test
    void wildcardMatchesEverything() {
        var rules = new ParameterSetting.AuditRecordRules();
        rules.setMode("WHITELIST");
        rules.setOperations(List.of("*"));
        var audit = new ParameterSetting.Audit();
        audit.setAuditRecordRules(rules);
        when(parameterGroups.getAudit()).thenReturn(Mono.just(audit));

        assertThat(evaluator.shouldRecord("ANY", "Anything").block()).isTrue();
    }
}
