package cn.tolim.pudans.notification.configuration;

import cn.tolim.pudans.notification.controller.NotificationApiPaths;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSentinelRuleTests {

    private static final String SEND_BATCH = NotificationApiPaths.SEND + "/batch";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void flowRulesCoverSendEndpoints() throws IOException {
        FlowRule[] rules = jsonMapper.readValue(read("pudans-notification-sentinel-flow.json"),
                FlowRule[].class);
        assertThat(rules).hasSize(2);
        assertThat(Arrays.stream(rules).map(FlowRule::getResource))
                .containsExactlyInAnyOrder(NotificationApiPaths.SEND, SEND_BATCH);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.getGrade()).isEqualTo(RuleConstant.FLOW_GRADE_QPS);
            assertThat(rule.getCount()).isPositive();
            assertThat(rule.getControlBehavior()).isEqualTo(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        });
    }

    @Test
    void degradeRulesCoverSendEndpoints() throws IOException {
        DegradeRule[] rules = jsonMapper.readValue(
                read("pudans-notification-sentinel-degrade.json"), DegradeRule[].class);
        assertThat(rules).hasSize(2);
        assertThat(Arrays.stream(rules).map(DegradeRule::getResource))
                .containsExactlyInAnyOrder(NotificationApiPaths.SEND, SEND_BATCH);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.getGrade()).isEqualTo(RuleConstant.DEGRADE_GRADE_RT);
            assertThat(rule.getCount()).isPositive();
            assertThat(rule.getTimeWindow()).isPositive();
            assertThat(rule.getSlowRatioThreshold()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void seedReferencesRuleDataIds() throws IOException {
        String seed = read("pudans-notification.yaml");
        assertThat(seed).contains("data-id: pudans-notification-sentinel-flow.json");
        assertThat(seed).contains("data-id: pudans-notification-sentinel-degrade.json");
    }

    private String read(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as("missing test resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}