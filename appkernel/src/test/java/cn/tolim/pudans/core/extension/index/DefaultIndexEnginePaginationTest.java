package cn.tolim.pudans.core.extension.index;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.contract.extension.PageRequestImpl;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.convert.support.DefaultConversionService;

class DefaultIndexEnginePaginationTest {

    private static final List<String> NAMES = IntStream.rangeClosed(1, 30)
            .mapToObj(number -> "user-" + String.format("%02d", number))
            .toList();

    @Test
    void pageRequestImplTreatsPagesBelowOneAsTheFirstPage() {
        assertThat(PageRequestImpl.of(0, 10).getPageNumber()).isEqualTo(1);
        assertThat(PageRequestImpl.of(1, 10).getPageNumber()).isEqualTo(1);
        assertThat(PageRequestImpl.of(2, 10).getPageNumber()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"1, 10", "2, 10", "3, 10", "2, 25"})
    @SuppressWarnings("unchecked")
    void indexPaginationUsesOneBasedPages(int page, int size) throws Exception {
        var engine = new DefaultIndexEngine(new DefaultConversionService());
        Method method = DefaultIndexEngine.class.getDeclaredMethod(
                "paginate", Collection.class, cn.tolim.pudans.contract.extension.PageRequest.class, List.class);
        method.setAccessible(true);

        var result = (ListResult<String>) method.invoke(
                engine, NAMES, PageRequestImpl.of(page, size), List.of());

        var expected = NAMES.subList((page - 1) * size, Math.min(page * size, NAMES.size()));
        assertThat(result.getItems())
                .as("index pagination for page %s, size %s", page, size)
                .containsExactlyElementsOf(expected);
    }
}
