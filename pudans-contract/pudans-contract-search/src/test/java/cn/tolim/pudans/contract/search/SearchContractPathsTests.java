package cn.tolim.pudans.contract.search;

import static cn.tolim.pudans.contract.search.SearchApiPaths.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Contract path consistency tests
class SearchContractPathsTests {

    @Test
    void pathsAreNestedUnderBase() {
        assertThat(INDEXES).startsWith(BASE);
        assertThat(INDEX_DETAIL).startsWith(BASE);
        assertThat(INDEX_DOCUMENTS).startsWith(BASE);
        assertThat(DOCUMENTS_BATCH).startsWith(BASE);
        assertThat(DOCUMENT_DETAIL).startsWith(BASE);
        assertThat(QUERY).startsWith(BASE);
    }
}