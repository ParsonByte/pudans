package cn.tolim.pudans.contract.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiPlanTest {

    @Test
    void acceptsTheDefaultSpec() {
        assertThat(new AiPlan.Spec().violation()).isNull();
    }

    @Test
    void rejectsNegativeRateLimits() {
        var spec = new AiPlan.Spec();
        spec.getRateLimit().setMaxConcurrency(-1);
        assertThat(spec.violation()).contains("并发");

        spec.getRateLimit().setMaxConcurrency(0);
        spec.getRateLimit().setMaxRpm(-1);
        assertThat(spec.violation()).contains("每分钟");
    }

    @Test
    void requiresADowngradeRouteGroupForDowngradeFallback() {
        var spec = new AiPlan.Spec();
        spec.getFallback().setOnFiveHourExhausted(AiPlan.FallbackAction.DOWNGRADE);
        assertThat(spec.violation()).contains("降级");

        spec.getFallback().setDowngradeRouteGroup("cheap-group");
        assertThat(spec.violation()).isNull();
        assertThat(spec.getFallback().downgrades()).isTrue();
    }

    @Test
    void downgradesOnlyWhenConfiguredWithARouteGroup() {
        var fallback = new AiPlan.Fallback();
        assertThat(fallback.downgrades()).isFalse();

        fallback.setOnFiveHourExhausted(AiPlan.FallbackAction.DOWNGRADE);
        assertThat(fallback.downgrades()).isFalse();

        fallback.setDowngradeRouteGroup(" ");
        assertThat(fallback.downgrades()).isFalse();

        fallback.setDowngradeRouteGroup("cheap-group");
        assertThat(fallback.downgrades()).isTrue();
    }

    @Test
    void rejectsBlankAccessEntries() {
        var spec = new AiPlan.Spec();
        spec.getAccess().setRouteGroups(List.of("fast", " "));
        assertThat(spec.violation()).contains("路由组");

        spec.getAccess().setRouteGroups(List.of("fast"));
        spec.getAccess().setModels(List.of(""));
        assertThat(spec.violation()).contains("模型");

        spec.getAccess().setModels(List.of("model-a"));
        spec.getAccess().setFeatures(Arrays.asList(AiPlan.Feature.CHAT, null));
        assertThat(spec.violation()).contains("功能");
    }

    @Test
    void emptyAccessListsAllowEverything() {
        var access = new AiPlan.Access();

        assertThat(access.allowsRouteGroup(null)).isTrue();
        assertThat(access.allowsRouteGroup("fast")).isTrue();
        assertThat(access.allowsModel(null)).isTrue();
        assertThat(access.allowsModel("model-a")).isTrue();
        assertThat(access.allowsFeature(null)).isTrue();
        assertThat(access.allowsFeature(AiPlan.Feature.CHAT)).isTrue();
    }

    @Test
    void configuredAccessListsRestrictValues() {
        var access = new AiPlan.Access();
        access.setRouteGroups(List.of("fast"));
        access.setModels(List.of("model-a"));
        access.setFeatures(List.of(AiPlan.Feature.CHAT));

        assertThat(access.allowsRouteGroup("fast")).isTrue();
        assertThat(access.allowsRouteGroup("pro")).isFalse();
        assertThat(access.allowsRouteGroup(null)).isFalse();
        assertThat(access.allowsModel("model-a")).isTrue();
        assertThat(access.allowsModel("model-b")).isFalse();
        assertThat(access.allowsModel(null)).isFalse();
        assertThat(access.allowsFeature(AiPlan.Feature.CHAT)).isTrue();
        assertThat(access.allowsFeature(AiPlan.Feature.AGENT)).isFalse();
        assertThat(access.allowsFeature(null)).isFalse();
    }
}