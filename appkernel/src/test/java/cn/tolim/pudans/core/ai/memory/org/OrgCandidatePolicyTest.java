package cn.tolim.pudans.core.ai.memory.org;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.memory.ExtractedMemory;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.SceneSegment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrgCandidatePolicyTest {

    @Test
    void treatsInstructionAtThePriorityThresholdAsACandidate() {
        assertThat(OrgCandidatePolicy.isCandidate(memory(MemoryType.INSTRUCTION, 70, Map.of())))
                .isTrue();
    }

    @Test
    void treatsWorkMethodAtThePriorityThresholdAsACandidate() {
        assertThat(OrgCandidatePolicy.isCandidate(memory(MemoryType.WORK_METHOD, 70, Map.of())))
                .isTrue();
    }

    @Test
    void rejectsInstructionBelowThePriorityThreshold() {
        assertThat(OrgCandidatePolicy.isCandidate(memory(MemoryType.INSTRUCTION, 69, Map.of())))
                .isFalse();
    }

    @Test
    void honorsTheMetadataFlagForAReusableType() {
        assertThat(OrgCandidatePolicy.isCandidate(
                        memory(MemoryType.WORK_FACT, 50, Map.of("orgCandidate", true))))
                .isTrue();
    }

    @Test
    void honorsATextMetadataFlag() {
        assertThat(OrgCandidatePolicy.isCandidate(
                        memory(MemoryType.WORK_ARTIFACT, 50, Map.of("orgCandidate", "true"))))
                .isTrue();
    }

    @Test
    void neverTreatsPersonalTypesAsCandidates() {
        for (var type : List.of(MemoryType.PERSONA, MemoryType.EPISODIC, MemoryType.WORK_TASK)) {
            assertThat(OrgCandidatePolicy.isCandidate(memory(type, 95, Map.of("orgCandidate", true))))
                    .as(type.name())
                    .isFalse();
        }
    }

    @Test
    void reportsACandidateWhenAnySceneMemoryQualifies() {
        var scenes = List.of(
                new SceneSegment(
                        "偏好", List.of("101"), List.of(memory(MemoryType.EPISODIC, 50, Map.of()))),
                new SceneSegment(
                        "方法", List.of("102"), List.of(memory(MemoryType.WORK_METHOD, 80, Map.of()))));

        assertThat(OrgCandidatePolicy.hasCandidate(scenes)).isTrue();
    }

    @Test
    void reportsNoCandidateForEmptyScenes() {
        assertThat(OrgCandidatePolicy.hasCandidate(List.of())).isFalse();
        assertThat(OrgCandidatePolicy.hasCandidate(null)).isFalse();
    }

    private static ExtractedMemory memory(MemoryType type, int priority, Map<String, Object> metadata) {
        return new ExtractedMemory("m-1", "可复用事实", type, priority, "场景", List.of("101"), metadata);
    }
}
