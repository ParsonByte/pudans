package cn.tolim.pudans.extension.contract;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.infra.contract.utils.GenericClassUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.Data;
import org.springframework.util.Assert;

@Data
public class ListResult<T> implements Iterable<T>, Supplier<Stream<T>> {

    @Schema(
            description = "Page number, starts from 1. Empty results may report 0.",
            requiredMode = REQUIRED)
    private final int page;

    @Schema(
            description = "Size of each page. If not set or equal to 0, it means no pagination.",
            requiredMode = REQUIRED)
    private final int size;

    @Schema(description = "Total elements.", requiredMode = REQUIRED)
    private final long total;

    @Schema(description = "A chunk of items.", requiredMode = REQUIRED)
    private final List<T> items;

    // 校验并归一化分页参数，拒绝负总数。
    @JsonCreator
    public ListResult(
            @JsonProperty("page") int page,
            @JsonProperty("size") int size,
            @JsonProperty("total") long total,
            @JsonProperty("items") List<T> items) {
        Assert.isTrue(total >= 0, "总元素数必须大于或等于 0");
        if (page < 0) {
            page = 0;
        }
        if (size < 0) {
            size = 0;
        }
        if (items == null) {
            items = Collections.emptyList();
        }
        this.page = page;
        this.size = size;
        this.total = total;
        this.items = items;
    }

    // 单页结果，不参与分页计算。
    public ListResult(List<T> items) {
        this(0, 0, items.size(), items);
    }

    // 按 scheme 的元素类型生成带泛型的文档类。
    public static Class<?> generateGenericClass(Scheme scheme) {
        return GenericClassUtils.generateConcreteClass(ListResult.class, scheme.type(), () -> {
            var pkgName = scheme.type().getPackageName();
            return pkgName + '.' + scheme.groupVersionKind().kind() + "List";
        });
    }

    // 按元素类型生成 ListResult 的具体类。
    public static <T> Class<?> generateGenericClass(Class<T> type) {
        return GenericClassUtils.generateConcreteClass(ListResult.class, type, () -> type.getName() + "List");
    }

    // 返回空结果，作为无数据时的统一响应。
    public static <T> ListResult<T> emptyResult() {
        return new ListResult<>(List.of());
    }

    // 按页号与页大小截取列表，越界页返回空。
    public static <T> List<T> subList(List<T> list, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            return list;
        }
        List<T> listSort = new ArrayList<>();
        int total = list.size();
        int pageStart = page == 1 ? 0 : (page - 1) * size;
        int pageEnd = Math.min(total, page * size);
        if (total > pageStart) {
            listSort = list.subList(pageStart, pageEnd);
        }
        return listSort;
    }

    // 取第一项，空结果或 null 输入返回空。
    public static <T> Optional<T> first(ListResult<T> listResult) {
        return Optional.ofNullable(listResult)
                .map(ListResult::getItems)
                .map(list -> list.isEmpty() ? null : list.get(0));
    }

    // 当前页是否为第一页。
    @Schema(description = "Indicates whether current page is the first page.", requiredMode = REQUIRED)
    public boolean isFirst() {
        return !hasPrevious();
    }

    // 当前页是否为最后一页。
    @Schema(description = "Indicates whether current page is the last page.", requiredMode = REQUIRED)
    public boolean isLast() {
        return !hasNext();
    }

    // 根据页码与总页数判断是否存在下一页。
    @Schema(description = "Indicates whether current page has next page.", requiredMode = REQUIRED)
    @JsonProperty("hasNext")
    public boolean hasNext() {
        if (page <= 0) {
            return false;
        }
        return page < getTotalPages();
    }

    // 页码大于 1 时存在上一页。
    @Schema(description = "Indicates whether current page has previous page.", requiredMode = REQUIRED)
    @JsonProperty("hasPrevious")
    public boolean hasPrevious() {
        return page > 1;
    }

    // 遍历当前页元素，支持 for-each。
    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    // 无分页时视为一页，否则向上取整计算。
    @Schema(description = "Indicates total pages.", requiredMode = REQUIRED)
    @JsonProperty("totalPages")
    public long getTotalPages() {
        return size == 0 ? 1 : (total + size - 1) / size;
    }

    // 以 Stream 形式暴露当前页元素。
    @Override
    public Stream<T> get() {
        return items.stream();
    }
}
