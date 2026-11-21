package cn.tolim.pudans.core.ai.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiPlan;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PlanCatalogTest {

    @Mock
    ReactiveExtensionClient client;

    @Test
    void returnsAnEnabledPlan() {
        var plan = PlanTestFixtures.plan("standard");
        var service = new PlanCatalog(client);
        when(client.get(AiPlan.class, "standard")).thenReturn(Mono.just(plan));

        StepVerifier.create(service.requireEnabled("standard"))
                .assertNext(found -> assertThat(found).isSameAs(plan))
                .verifyComplete();
    }

    @Test
    void rejectsAMissingPlan() {
        var service = new PlanCatalog(client);
        when(client.get(AiPlan.class, "missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.requireEnabled("missing"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue("code", PlanCatalog.CODE_INVALID))
                .verify();
    }

    @Test
    void validatesTheRequiredQuotaShape() {
        assertInvalid(plan -> plan.getSpec().setDisplayName(" "));
        assertInvalid(plan -> plan.getSpec().setEnabled(false));
        assertInvalid(plan -> plan.getSpec().getQuota().getFiveHour().setLimit(0));
        assertInvalid(plan -> plan.getSpec().getQuota().getWeekly().setLimit(10));
        assertInvalid(plan -> plan.getSpec().getQuota().getWeekly().setResetDayOfWeek(null));
        assertInvalid(plan -> plan.getSpec().getQuota().getWeekly().setResetHour(24));
        assertInvalid(plan -> plan.getSpec().setToleranceUnits(-1));
        assertInvalid(plan -> plan.getSpec().getVouchers().setFiveHour(-1));
        assertInvalid(plan -> plan.getSpec().getVouchers().setPeriod(null));
        assertInvalid(plan -> plan.setSpec(null));
    }

    @Test
    void treatsInvalidPlansAsUnusable() {
        var plan = PlanTestFixtures.plan("standard");
        plan.getSpec().setEnabled(false);
        var service = new PlanCatalog(client);

        assertThat(service.usable(plan)).isFalse();
        assertThat(service.usable(PlanTestFixtures.plan("standard"))).isTrue();
        assertThat(service.usable(null)).isFalse();
    }

    private static void assertInvalid(java.util.function.Consumer<AiPlan> mutation) {
        var plan = PlanTestFixtures.plan("standard");
        mutation.accept(plan);
        ThrowingCallable validation = () -> PlanCatalog.requireValid(plan);
        assertThatThrownBy(validation)
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", PlanCatalog.CODE_INVALID);
    }
}