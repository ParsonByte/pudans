package cn.tolim.pudans.contract.job;

import static cn.tolim.pudans.contract.job.JobApiPaths.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Contract path consistency tests
class JobContractPathsTests {

    @Test
    void pathsAreNestedUnderBase() {
        assertThat(JOBS).startsWith(BASE);
        assertThat(JOB_DETAIL).startsWith(BASE);
        assertThat(JOB_ENABLE).startsWith(BASE);
        assertThat(JOB_DISABLE).startsWith(BASE);
        assertThat(JOB_TRIGGER).startsWith(BASE);
        assertThat(JOB_HANDLERS).startsWith(BASE);
        assertThat(EXECUTIONS).startsWith(BASE);
        assertThat(EXECUTION_DETAIL).startsWith(BASE);
        assertThat(EXECUTION_CANCEL).startsWith(BASE);
        assertThat(EXECUTION_STATS).startsWith(BASE);
        assertThat(EXECUTOR_STATUS).startsWith(BASE);
        assertThat(EXECUTOR_SCHEDULED).startsWith(BASE);
    }
}