package cn.tolim.pudans.plugin.resources;

import static org.springframework.http.MediaType.ALL;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import cn.tolim.pudans.contract.core.extension.ReverseProxy;
import cn.tolim.pudans.contract.core.extension.ReverseProxy.FileReverseProxyProvider;
import cn.tolim.pudans.contract.core.extension.ReverseProxy.ReverseProxyRule;
import cn.tolim.pudans.contract.infra.utils.PathUtils;
import cn.tolim.pudans.infra.exception.NotFoundException;
import cn.tolim.pudans.plugin.PluginConst;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.pf4j.PluginManager;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;


@Slf4j
@Component
@AllArgsConstructor
public class ReverseProxyRouterFunctionFactory {

    private final PluginManager pluginManager;
    private final ApplicationContext applicationContext;
    private final WebProperties webProperties;

    public static String buildRoutePath(String pluginId, ReverseProxyRule reverseProxyRule) {
        return PathUtils.combinePath(PluginConst.assetsRoutePrefix(pluginId), reverseProxyRule.path());
    }

    public @Nullable RouterFunction<ServerResponse> create(ReverseProxy reverseProxy, String pluginName) {
        return createReverseProxyRouterFunction(reverseProxy, nullSafePluginName(pluginName));
    }

    private @Nullable RouterFunction<ServerResponse> createReverseProxyRouterFunction(
            ReverseProxy reverseProxy, String pluginName) {
        Assert.notNull(reverseProxy, "The reverseProxy must not be null.");
        var rules = getReverseProxyRules(reverseProxy);
        var cacheProperties = webProperties.getResources().getCache();
        var useLastModified = cacheProperties.isUseLastModified();
        var cacheControl = cacheProperties.getCachecontrol().toHttpCacheControl();
        if (cacheControl == null) {
            cacheControl = CacheControl.empty();
        }
        var finalCacheControl = cacheControl;
        return rules.stream()
                .map(rule -> {
                    String routePath = buildRoutePath(pluginName, rule);
                    log.debug("Plugin [{}] registered reverse proxy route path [{}]", pluginName, routePath);
                    return RouterFunctions.route(GET(routePath).and(accept(ALL)), request -> {
                        var resource = loadResourceByFileRule(pluginName, rule, request);
                        if (!resource.exists()) {
                            return Mono.error(new NoResourceFoundException(request.uri(), routePath));
                        }
                        if (!useLastModified) {
                            return ServerResponse.ok()
                                    .cacheControl(finalCacheControl)
                                    .body(BodyInserters.fromResource(resource));
                        }
                        Instant lastModified;
                        try {
                            lastModified = Instant.ofEpochMilli(resource.lastModified());
                        } catch (IOException e) {
                            if (e instanceof FileNotFoundException) {
                                return Mono.error(new NoResourceFoundException(request.uri(), routePath));
                            }
                            return Mono.error(e);
                        }
                        return request.checkNotModified(lastModified)
                                .switchIfEmpty(Mono.defer(() -> ServerResponse.ok()
                                        .cacheControl(finalCacheControl)
                                        .lastModified(lastModified)
                                        .body(BodyInserters.fromResource(resource))));
                    });
                })
                .reduce(RouterFunction::and)
                .orElse(null);
    }

    private String nullSafePluginName(String pluginName) {
        return pluginName == null ? PluginConst.SYSTEM_PLUGIN_NAME : pluginName;
    }

    private List<ReverseProxyRule> getReverseProxyRules(ReverseProxy reverseProxy) {
        return reverseProxy.getRules();
    }

    private Resource loadResourceByFileRule(String pluginName, ReverseProxyRule rule, ServerRequest request) {
        Assert.notNull(rule.file(), "File rule must not be null.");
        FileReverseProxyProvider file = rule.file();
        String directory = file.directory();


        String filename;
        String configuredFilename = file.filename();
        if (StringUtils.isNotBlank(configuredFilename)) {
            filename = configuredFilename;
        } else {
            String routePath = buildRoutePath(pluginName, rule);
            PathContainer pathContainer = PathPatternParser.defaultInstance
                    .parse(routePath)
                    .extractPathWithinPattern(PathContainer.parsePath(request.path()));
            filename = pathContainer.value();
        }

        String filePath = PathUtils.combinePath(directory, filename);
        return getResourceLoader(pluginName).getResource(filePath);
    }

    private ResourceLoader getResourceLoader(String pluginName) {
        if (PluginConst.SYSTEM_PLUGIN_NAME.equals(pluginName)) {
            return applicationContext;
        }
        DefaultResourceLoader resourceLoader = BundleResourceUtils.getResourceLoader(pluginManager, pluginName);
        if (resourceLoader == null) {
            throw new NotFoundException("Plugin [" + pluginName + "] not found.");
        }
        return resourceLoader;
    }
}
