package cn.tolim.pudans.extension.index;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.audit.resource.AuditRecord;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.index.IndexSpecs;
import cn.tolim.pudans.contract.extension.index.SingleValueIndexSpec;
import cn.tolim.pudans.contract.extension.index.query.BetweenCondition;
import cn.tolim.pudans.contract.extension.index.query.NotBetweenCondition;
import org.junit.jupiter.api.Test;

class SingleValueIndexNotBetweenTest {

    private static SingleValueIndex<AuditRecord, Integer> index() {
        return new SingleValueIndex<>(spec());
    }

    @SuppressWarnings("unchecked")
    private static SingleValueIndexSpec<AuditRecord, Integer> spec() {
        return (SingleValueIndexSpec<AuditRecord, Integer>) IndexSpecs.<AuditRecord, Integer>single(
                        "stepIndex", Integer.class)
                .indexFunc(AuditRecord::getStepIndex)
                .build();
    }

    private static AuditRecord record(int stepIndex) {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName("record-" + stepIndex);
        record.setMetadata(metadata);
        record.setStepIndex(stepIndex);
        return record;
    }

    private static void insert(SingleValueIndex<AuditRecord, Integer> index, int stepIndex) {
        var operation = index.prepareInsert(record(stepIndex));
        operation.prepare();
        operation.commit();
    }

    @Test
    void negatingAnInclusiveBetweenExcludesTheBoundaryValues() {
        var index = index();
        for (var step = 1; step <= 5; step++) {
            insert(index, step);
        }

        var negated = (NotBetweenCondition) new BetweenCondition("stepIndex", 2, true, 4, true).not();
        var result = index.notBetween(2, negated.fromInclusive(), 4, negated.toInclusive());

        assertThat(result).containsExactlyInAnyOrder("record-1", "record-5");
    }

    @Test
    void negatingAnExclusiveBetweenKeepsTheBoundaryValues() {
        var index = index();
        for (var step = 1; step <= 5; step++) {
            insert(index, step);
        }

        var negated = (NotBetweenCondition) new BetweenCondition("stepIndex", 2, false, 4, false).not();
        var result = index.notBetween(2, negated.fromInclusive(), 4, negated.toInclusive());

        assertThat(result).containsExactlyInAnyOrder("record-1", "record-2", "record-4", "record-5");
    }
}
