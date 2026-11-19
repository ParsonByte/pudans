package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PersonaTriggerTest {

    private final PersonaTrigger trigger = new PersonaTrigger();

    @Test
    void explicitRequestWins() {
        var reason = trigger.evaluate(new PersonaTrigger.State(true, true, true, true, 5, 100, 10));

        assertThat(reason).isEqualTo(PersonaTrigger.Reason.EXPLICIT_REQUEST);
    }

    @Test
    void triggersColdStartAfterFirstExtractionWithoutPersona() {
        var reason = trigger.evaluate(new PersonaTrigger.State(false, false, false, true, 2, 0, 10));

        assertThat(reason).isEqualTo(PersonaTrigger.Reason.COLD_START);
    }

    @Test
    void doesNotColdStartWithoutSceneBlocks() {
        var reason = trigger.evaluate(new PersonaTrigger.State(false, false, false, false, 2, 0, 10));

        assertThat(reason).isEqualTo(PersonaTrigger.Reason.NONE);
    }

    @Test
    void triggersRecoveryWhenPersonaBodyIsMissing() {
        var reason = trigger.evaluate(new PersonaTrigger.State(false, true, false, true, 2, 0, 10));

        assertThat(reason).isEqualTo(PersonaTrigger.Reason.RECOVERY);
    }

    @Test
    void triggersOnFirstSceneBlock() {
        var reason = trigger.evaluate(new PersonaTrigger.State(false, true, true, true, 1, 1, 10));

        assertThat(reason).isEqualTo(PersonaTrigger.Reason.FIRST_SCENE);
    }

    @Test
    void triggersWhenMemoryThresholdIsReached() {
        var reason = trigger.evaluate(new PersonaTrigger.State(false, true, true, true, 3, 10, 10));

        assertThat(reason).isEqualTo(PersonaTrigger.Reason.THRESHOLD);
    }

    @Test
    void doesNotTriggerBelowThreshold() {
        var reason = trigger.evaluate(new PersonaTrigger.State(false, true, true, true, 3, 9, 10));

        assertThat(reason).isEqualTo(PersonaTrigger.Reason.NONE);
        assertThat(reason.triggersGeneration()).isFalse();
    }
}
