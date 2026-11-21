package cn.tolim.pudans.extension.contract.endpoint;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.ReactiveSortHandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

public interface SortResolver {

    SortResolver defaultInstance = new DefaultSortResolver();

    // 从请求中解析排序条件。
    Sort resolve(ServerWebExchange exchange);

    class DefaultSortResolver extends ReactiveSortHandlerMethodArgumentResolver implements SortResolver {

        // 未指定排序时返回无序，避免隐式排序。
        @Override
        protected Sort getDefaultFromAnnotationOrFallback(@Nullable MethodParameter parameter) {
            return Sort.unsorted();
        }

        // 使用排序解析器处理当前请求。
        @Override
        public Sort resolve(ServerWebExchange exchange) {
            return resolveArgumentValue(null, null, exchange);
        }
    }
}
