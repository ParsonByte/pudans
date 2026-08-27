package cn.tolim.pudans.infra.config;

import static org.springframework.util.ResourceUtils.FILE_URL_PREFIX;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.infra.SecureRequestMappingHandlerAdapter;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.webfilter.AdditionalWebFilterChainProxy;
import cn.tolim.pudans.plugin.endpoint.CustomEndpointsBuilder;
import cn.tolim.pudans.plugin.endpoint.WebSocketHandlerMapping;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.autoconfigure.WebFluxRegistrations;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.filter.reactive.UrlHandlerFilter;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.resource.EncodedResourceResolver;
import org.springframework.web.reactive.resource.PathResourceResolver;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.reactive.result.view.ViewResolutionResultHandler;
import org.springframework.web.reactive.result.view.ViewResolver;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@RequiredArgsConstructor
public class WebFluxConfig implements WebFluxConfigurer {

    private final JsonMapper jsonMapper;

    private final PudansProperties pudansProp;

    private final WebProperties webProperties;

    private final ApplicationContext applicationContext;

    @Bean
    WebFluxRegistrations webFluxRegistrations() {
        return new WebFluxRegistrations() {
            @Override
            public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {

                return new SecureRequestMappingHandlerAdapter();
            }
        };
    }

    @Bean
    ServerResponse.Context context(CodecConfigurer codec, ViewResolutionResultHandler resultHandler) {
        return new ServerResponse.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return codec.getWriters();
            }

            @Override
            public List<ViewResolver> viewResolvers() {
                return resultHandler.getViewResolvers();
            }
        };
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
        configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> customEndpoints(ApplicationContext context) {
        var builder = new CustomEndpointsBuilder();
        context.getBeansOfType(CustomEndpoint.class).values().forEach(builder::add);
        return builder.build();
    }

    @Bean
    public WebSocketHandlerMapping webSocketHandlerMapping() {
        WebSocketHandlerMapping handlerMapping = new WebSocketHandlerMapping();
        handlerMapping.setOrder(-2);
        return handlerMapping;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var resourceProperties = webProperties.getResources();
        var cacheControl = resourceProperties.getCache().getCachecontrol().toHttpCacheControl();
        if (cacheControl == null) {
            cacheControl = CacheControl.empty();
        }
        final var useLastModified = resourceProperties.getCache().isUseLastModified();
        var staticPath = pudansProp.getWorkDir().resolve("static");
        registry.addResourceHandler("/**")
                .addResourceLocations(FILE_URL_PREFIX + staticPath + "/")
                .addResourceLocations(resourceProperties.getStaticLocations())
                .setCacheControl(cacheControl)
                .setUseLastModified(useLastModified)
                .resourceChain(true)
                .addResolver(new EncodedResourceResolver())
                .addResolver(new PathResourceResolver());
    }


    @Bean
    @Order(-101)
    AdditionalWebFilterChainProxy additionalWebFilterChainProxy(ExtensionGetter extensionGetter) {
        return new AdditionalWebFilterChainProxy(extensionGetter);
    }

    @Bean
    @Order(-102)
    ServerWebExchangeContextFilter serverWebExchangeContextFilter() {
        return new ServerWebExchangeContextFilter();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    UrlHandlerFilter urlHandlerFilter() {
        return UrlHandlerFilter.trailingSlashHandler("/**").mutateRequest().build();
    }
}
