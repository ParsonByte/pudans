package cn.tolim.pudans.core.ai.memory;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class JevGate {

    private static final String NO_CHANGED_SCENES = "no_changed_scenes";
    private static final String MISSING_CURRENT_PERSONA = "missing_current_persona";
    private static final String CLASSIFIER_FAILED = "classifier_failed";
    private static final String DECIDED = "jev_decision";

    public JevDecision evaluate(
            String currentPersona, List<ChangedScene> changedScenes, ChangeClassifier classifier) {
        Objects.requireNonNull(classifier, "classifier");
        if (changedScenes == null || changedScenes.isEmpty()) {
            return new JevDecision(JevDecisionType.NOT_APPLICABLE, JevChangeType.NONE, NO_CHANGED_SCENES);
        }
        if (currentPersona == null || currentPersona.isBlank()) {
            return new JevDecision(JevDecisionType.UNAVAILABLE, JevChangeType.NONE, MISSING_CURRENT_PERSONA);
        }
        var highest = JevChangeType.NONE;
        try {
            for (var scene : changedScenes) {
                var label = classifier.classify(currentPersona, scene);
                if (label == null) {
                    return new JevDecision(JevDecisionType.UNAVAILABLE, JevChangeType.NONE, CLASSIFIER_FAILED);
                }
                if (label.priority() > highest.priority()) {
                    highest = label;
                }
            }
        } catch (RuntimeException exception) {
            return new JevDecision(JevDecisionType.UNAVAILABLE, JevChangeType.NONE, CLASSIFIER_FAILED);
        }
        var decision = highest.requiresImmediateGeneration() ? JevDecisionType.IMMEDIATE : JevDecisionType.DEFERRED;
        return new JevDecision(decision, highest, DECIDED);
    }

    public record ChangedScene(String sceneKey, String summary, String content) {

        public ChangedScene {
            if (sceneKey == null || sceneKey.isBlank()) {
                throw new IllegalArgumentException("sceneKey 不能为空");
            }
            Objects.requireNonNull(content, "content");
            summary = summary == null ? "" : summary;
        }
    }

    @FunctionalInterface
    public interface ChangeClassifier {
        JevChangeType classify(String currentPersona, ChangedScene scene);
    }
}
