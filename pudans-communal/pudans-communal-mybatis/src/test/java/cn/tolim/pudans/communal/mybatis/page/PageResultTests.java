package cn.tolim.pudans.communal.mybatis.page;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTests {

    @Test
    void computesTotalPagesAndNextFlag() {
        PageResult<String> result = new PageResult<>(List.of("a", "b"), 1, 2, 5);

        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void lastPageHasNoNext() {
        PageResult<String> result = new PageResult<>(List.of("e"), 3, 2, 5);

        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void emptyResultHasNoPages() {
        PageResult<String> result = PageResult.empty(new PageQuery(1, 20));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void mapsItemsWithoutLosingMetadata() {
        PageResult<String> result = new PageResult<>(List.of("1", "2"), 2, 2, 6);

        PageResult<Integer> mapped = result.map(Integer::valueOf);

        assertThat(mapped.items()).containsExactly(1, 2);
        assertThat(mapped.page()).isEqualTo(2);
        assertThat(mapped.total()).isEqualTo(6);
    }

    @Test
    void copiesItemsImmutably() {
        PageResult<String> result = new PageResult<>(List.of("a"), 1, 10, 1);

        assertThat(result.items()).isUnmodifiable();
    }
}