package cn.tolim.pudans.contract.ai.resource;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.extension.Scheme;
import org.junit.jupiter.api.Test;

class AiPlanSchemeTest {

    @Test
    void buildsPlanSchemes() {
        var plan = Scheme.buildFromType(AiPlan.class);
        var binding = Scheme.buildFromType(AiPlanBinding.class);

        assertThat(plan.groupVersionKind().group()).isEqualTo("pudans.tolim.cn");
        assertThat(plan.groupVersionKind().version()).isEqualTo("v1alpha1");
        assertThat(plan.groupVersionKind().kind()).isEqualTo("AiPlan");
        assertThat(plan.plural()).isEqualTo("aiplans");
        assertThat(plan.singular()).isEqualTo("aiplan");

        assertThat(binding.groupVersionKind().kind()).isEqualTo("AiPlanBinding");
        assertThat(binding.plural()).isEqualTo("aiplanbindings");
        assertThat(binding.singular()).isEqualTo("aiplanbinding");

        assertThat(plan.openApiSchema()).isNotNull();
        assertThat(binding.openApiSchema()).isNotNull();
    }
}