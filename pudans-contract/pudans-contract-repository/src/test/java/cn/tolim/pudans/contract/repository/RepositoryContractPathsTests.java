package cn.tolim.pudans.contract.repository;

import static cn.tolim.pudans.contract.repository.RepositoryApiPaths.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Contract path consistency tests
class RepositoryContractPathsTests {

    @Test
    void pathsAreNestedUnderBase() {
        assertThat(FILES).startsWith(BASE);
        assertThat(FILE_DETAIL).startsWith(BASE);
        assertThat(FILE_CONTENT).startsWith(BASE);
        assertThat(FILE_URL).startsWith(BASE);
        assertThat(PROVIDERS).startsWith(BASE);
    }
}
