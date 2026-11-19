package cn.tolim.pudans.core.ai.memory;

import org.springframework.stereotype.Service;

@Service
public final class PersonaTrigger {

    public Reason evaluate(State state) {
        if (state.explicitRequested()) {
            return Reason.EXPLICIT_REQUEST;
        }
        if (state.scenesProcessed() > 0 && state.hasScenes() && !state.personaGenerated()) {
            return Reason.COLD_START;
        }
        if (state.personaGenerated() && state.hasScenes() && !state.personaBodyPresent()) {
            return Reason.RECOVERY;
        }
        if (state.scenesProcessed() == 1 && state.memoriesSinceLastPersona() > 0) {
            return Reason.FIRST_SCENE;
        }
        if (state.memoriesSinceLastPersona() >= state.threshold()) {
            return Reason.THRESHOLD;
        }
        return Reason.NONE;
    }

    public record State(
            boolean explicitRequested,
            boolean personaGenerated,
            boolean personaBodyPresent,
            boolean hasScenes,
            int scenesProcessed,
            int memoriesSinceLastPersona,
            int threshold) {

        public State {
            if (scenesProcessed < 0) {
                throw new IllegalArgumentException("scenesProcessed 不能为负数");
            }
            if (memoriesSinceLastPersona < 0) {
                throw new IllegalArgumentException("memoriesSinceLastPersona 不能为负数");
            }
            if (threshold < 1) {
                throw new IllegalArgumentException("threshold 必须大于 0");
            }
        }
    }

    public enum Reason {
        EXPLICIT_REQUEST,
        COLD_START,
        RECOVERY,
        FIRST_SCENE,
        THRESHOLD,
        NONE;

        public boolean triggersGeneration() {
            return this != NONE;
        }
    }
}
