package cn.tolim.pudans.communal.mybatis.page;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PageQueryTests {

    @Test
    void defaultsAreApplied() {
        PageQuery query = PageQuery.of(null, null);

        assertThat(query.page()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(query.size()).isEqualTo(PageQuery.DEFAULT_SIZE);
    }

    @Test
    void offsetIsBasedOnPageNumber() {
        PageQuery query = new PageQuery(3, 25);

        assertThat(query.offset()).isEqualTo(50L);
        assertThat(query.limit()).isEqualTo(25);
    }

    @Test
    void rejectsInvalidPage() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(0, 10));
    }

    @Test
    void rejectsUnboundedSize() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(1, PageQuery.MAX_SIZE + 1));
    }
}