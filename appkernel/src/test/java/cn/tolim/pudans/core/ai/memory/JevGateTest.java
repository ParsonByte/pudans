package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JevGateTest {

    private final JevGate gate = new JevGate();

    @Test
    void conflictWinsRegardlessOfSceneOrder() {
        var scenes = List.of(scene("s1"), scene("s2"), scene("s3"), scene("s4"), scene("s5"));
        var labels = Map.of(
                "s1", JevChangeType.NONE,
                "s2", JevChangeType.REINFORCEMENT,
                "s3", JevChangeType.NEW_RULE,
                "s4", JevChangeType.BOUNDARY_CHANGE,
                "s5", JevChangeType.CONFLICT);

        var decision = gate.evaluate("current persona", scenes, classifier(labels));

        assertThat(decision.changeType()).isEqualTo(JevChangeType.CONFLICT);
        assertThat(decision.decision()).isEqualTo(JevDecisionType.IMMEDIATE);
        assertThat(decision.shouldGenerate()).isTrue();
    }

    @Test
    void boundaryChangeWinsOverNewRuleReinforcementAndNone() {
        var scenes = List.of(scene("s1"), scene("s2"), scene("s3"));
        var labels = Map.of(
                "s1", JevChangeType.NONE,
                "s2", JevChangeType.NEW_RULE,
                "s3", JevChangeType.BOUNDARY_CHANGE);

        var decision = gate.evaluate("current persona", scenes, classifier(labels));

        assertThat(decision.changeType()).isEqualTo(JevChangeType.BOUNDARY_CHANGE);
        assertThat(decision.decision()).isEqualTo(JevDecisionType.IMMEDIATE);
    }

    @Test
    void newRuleWinsOverReinforcementAndNone() {
        var scenes = List.of(scene("s1"), scene("s2"), scene("s3"));
        var labels = Map.of(
                "s1", JevChangeType.NONE,
                "s2", JevChangeType.REINFORCEMENT,
                "s3", JevChangeType.NEW_RULE);

        var decision = gate.evaluate("current persona", scenes, classifier(labels));

        assertThat(decision.changeType()).isEqualTo(JevChangeType.NEW_RULE);
        assertThat(decision.decision()).isEqualTo(JevDecisionType.IMMEDIATE);
    }

    @Test
    void reinforcementDefersGeneration() {
        var scenes = List.of(scene("s1"), scene("s2"));
        var labels = Map.of("s1", JevChangeType.NONE, "s2", JevChangeType.REINFORCEMENT);

        var decision = gate.evaluate("current persona", scenes, classifier(labels));

        assertThat(decision.changeType()).isEqualTo(JevChangeType.REINFORCEMENT);
        assertThat(decision.decision()).isEqualTo(JevDecisionType.DEFERRED);
        assertThat(decision.shouldGenerate()).isFalse();
    }

    @Test
    void noneDefersGeneration() {
        var decision = gate.evaluate(
                "current persona", List.of(scene("s1")), classifier(Map.of("s1", JevChangeType.NONE)));

        assertThat(decision.changeType()).isEqualTo(JevChangeType.NONE);
        assertThat(decision.decision()).isEqualTo(JevDecisionType.DEFERRED);
    }

    @Test
    void reportsNotApplicableWithoutChangedScenes() {
        var decision = gate.evaluate("current persona", List.of(), classifier(Map.of()));

        assertThat(decision.decision()).isEqualTo(JevDecisionType.NOT_APPLICABLE);
        assertThat(decision.changeType()).isEqualTo(JevChangeType.NONE);
    }

    @Test
    void reportsUnavailableWithoutCurrentPersona() {
        var decision = gate.evaluate("  ", List.of(scene("s1")), (persona, scene) -> {
            throw new AssertionError("不应调用分类器");
        });

        assertThat(decision.decision()).isEqualTo(JevDecisionType.UNAVAILABLE);
        assertThat(decision.reason()).isEqualTo("missing_current_persona");
    }

    @Test
    void reportsUnavailableWhenClassifierFails() {
        var decision = gate.evaluate("current persona", List.of(scene("s1")), (persona, scene) -> {
            throw new IllegalStateException("upstream timeout");
        });

        assertThat(decision.decision()).isEqualTo(JevDecisionType.UNAVAILABLE);
        assertThat(decision.reason()).isEqualTo("classifier_failed");
    }

    @Test
    void reportsUnavailableWhenClassifierReturnsNoLabel() {
        var decision = gate.evaluate("current persona", List.of(scene("s1")), (persona, scene) -> null);

        assertThat(decision.decision()).isEqualTo(JevDecisionType.UNAVAILABLE);
        assertThat(decision.changeType()).isEqualTo(JevChangeType.NONE);
    }

    private static JevGate.ChangeClassifier classifier(Map<String, JevChangeType> labels) {
        return (persona, scene) -> labels.get(scene.sceneKey());
    }

    private static JevGate.ChangedScene scene(String sceneKey) {
        return new JevGate.ChangedScene(sceneKey, "summary", "content");
    }
}
