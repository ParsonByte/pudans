package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.SecureRequestMappingHandlerAdapter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.autoconfigure.WebFluxRegistrations;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.filter.reactive.UrlHandlerFilter;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.reactive.result.view.ViewResolutionResultHandler;
import org.springframework.web.reactive.result.view.ViewResolver;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@RequiredArgsConstructor
public class WebFluxConfig implements WebFluxConfigurer {

    private final JsonMapper jsonMapper;

    @Bean
    // 用安全适配器替换默认请求映射适配器。
    WebFluxRegistrations webFluxRegistrations() {
        return new WebFluxRegistrations() {
            // 返回带安全校验的处理器适配器。
            @Override
            public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {

                return new SecureRequestMappingHandlerAdapter();
            }
        };
    }

    @Bean
    // 为函数式端点提供编解码与视图上下文。
    ServerResponse.Context context(CodecConfigurer codec, ViewResolutionResultHandler resultHandler) {
        return new ServerResponse.Context() {
            // 复用全局配置的消息写入器。
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return codec.getWriters();
            }

            // 暴露结果处理器中的视图解析器。
            @Override
            public List<ViewResolver> viewResolvers() {
                return resultHandler.getViewResolvers();
            }
        };
    }

    // 提高内存上限并统一使用 Jackson 3 编解码。
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize((int) DataSize.ofMegabytes(16).toBytes());
        configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
        configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
    }

    // 收集扩展端点并注册为最高优先级路由。
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> customEndpoints(ApplicationContext context) {
        var builder = new CustomEndpointsBuilder();
        context.getBeansOfType(CustomEndpoint.class).values().forEach(builder::add);
        return builder.build();
    }

    // 让下游可通过上下文访问当前请求。
    @Bean
    @Order(-102)
    ServerWebExchangeContextFilter serverWebExchangeContextFilter() {
        return new ServerWebExchangeContextFilter();
    }

    // 统一处理末尾斜杠，避免重复路由。
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    UrlHandlerFilter urlHandlerFilter() {
        return UrlHandlerFilter.trailingSlashHandler("/**").mutateRequest().build();
    }
}
