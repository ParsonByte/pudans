package cn.tolim.pudans.extension.contract.router;

import static cn.tolim.pudans.extension.contract.Comparators.compareCreationTimestamp;
import static cn.tolim.pudans.extension.contract.Comparators.compareName;
import static cn.tolim.pudans.extension.contract.Comparators.nullsComparator;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.router.selector.SelectorUtil.labelAndFieldSelectorToListOptions;

import cn.tolim.pudans.extension.contract.endpoint.SortResolver;
import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.PageRequest;
import cn.tolim.pudans.extension.contract.PageRequestImpl;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ServerWebExchange;

public class SortableRequest extends IListRequest.QueryListRequest {

    protected final ServerWebExchange exchange;

    public SortableRequest(ServerWebExchange exchange) {
        super(exchange.getRequest().getQueryParams());
        this.exchange = exchange;
    }

    public static void buildParameters(Builder builder) {
        IListRequest.buildParameters(builder);
        builder.parameter(QueryParamBuildUtil.sortParameter());
    }

    @ArraySchema(
            uniqueItems = true,
            arraySchema =
            @Schema(
                    name = "sort",
                    description = "Sort property and direction of the list result. Support sorting based "
                            + "on attribute name path."),
            schema =
            @Schema(
                    description = "like field,asc or field,desc",
                    implementation = String.class,
                    example = "metadata.creationTimestamp,desc"))
    public Sort getSort() {
        return SortResolver.defaultInstance.resolve(exchange).and(defaultSort());
    }

    public ListOptions toListOptions() {
        return labelAndFieldSelectorToListOptions(getLabelSelector(), getFieldSelector());
    }

    public PageRequest toPageRequest() {
        return PageRequestImpl.of(getPage(), getSize(), getSort());
    }

    public <T extends Extension> Comparator<T> toComparator() {
        var sort = getSort();
        var fallbackComparator = Stream.<Comparator<T>>of(compareCreationTimestamp(false), compareName(true));
        var comparatorStream = sort.stream().map(order -> {
            var property = order.getProperty();
            var direction = order.getDirection();
            Function<T, Object> function = extension -> {
                BeanWrapper beanWrapper = new BeanWrapperImpl(extension);
                return beanWrapper.getPropertyValue(property);
            };
            var comparator = Comparator.comparing(function, nullsComparator(direction.isAscending()));
            if (direction.isDescending()) {
                comparator = comparator.reversed();
            }
            return comparator;
        });
        return Stream.concat(comparatorStream, fallbackComparator)
                .reduce(Comparator::thenComparing)
                .orElse(null);
    }
}
