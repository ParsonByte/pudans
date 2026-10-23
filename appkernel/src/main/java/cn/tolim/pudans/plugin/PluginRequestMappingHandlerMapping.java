package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.contract.plugin.ApiVersion;
import cn.tolim.pudans.infra.PudansAttributes;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodIntrospector;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public class PluginRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final MultiValueMap<String, RequestMappingInfo> pluginMappingInfo = new LinkedMultiValueMap<>();

    private final ConcurrentMap<Object, String> handlerPluginIds = new ConcurrentHashMap<>();

    @Override
    public Mono<HandlerMethod> getHandlerInternal(ServerWebExchange exchange) {
        return super.getHandlerInternal(exchange).doOnNext(handler -> {
            var pluginId = handlerPluginIds.get(handler.getBean());
            if (pluginId != null) {
                exchange.getAttributes().put(PudansAttributes.PLUGIN_ID_ATTRIBUTE, pluginId);
            }
        });
    }

    @Override
    protected void initHandlerMethods() {


    }


    public void registerHandlerMethods(String pluginId, Object handler) {
        handlerPluginIds.put(handler, pluginId);
        Class<?> handlerType = (handler instanceof String beanName
                ? obtainApplicationContext().getType(beanName)
                : handler.getClass());

        if (handlerType != null) {
            final Class<?> userType = ClassUtils.getUserClass(handlerType);
            Map<Method, RequestMappingInfo> methods =
                    MethodIntrospector.selectMethods(userType, (MethodIntrospector.MetadataLookup<RequestMappingInfo>)
                            method -> getPluginMappingForMethod(pluginId, method, userType));
            if (logger.isTraceEnabled()) {
                logger.trace(formatMappings(userType, methods));
            } else if (mappingsLogger.isDebugEnabled()) {
                mappingsLogger.debug(formatMappings(userType, methods));
            }
            methods.forEach((method, mapping) -> {
                Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
                registerHandlerMethod(handler, invocableMethod, mapping);
                pluginMappingInfo.add(pluginId, mapping);
            });
        }
    }

    private String formatMappings(Class<?> userType, Map<Method, RequestMappingInfo> methods) {
        String packageName = ClassUtils.getPackageName(userType);
        String formattedType = (StringUtils.hasText(packageName)
                ? Arrays.stream(packageName.split("\\."))
                .map(packageSegment -> packageSegment.substring(0, 1))
                .collect(Collectors.joining(".", "", "." + userType.getSimpleName()))
                : userType.getSimpleName());
        Function<Method, String> methodFormatter = method -> Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
        return methods.entrySet().stream()
                .map(e -> {
                    Method method = e.getKey();
                    return e.getValue() + ": " + method.getName() + methodFormatter.apply(method);
                })
                .collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
    }


    public void unregister(String pluginId) {
        Assert.notNull(pluginId, "The pluginId must not be null.");
        if (!pluginMappingInfo.containsKey(pluginId)) {
            return;
        }
        handlerPluginIds.entrySet().removeIf(entry -> pluginId.equals(entry.getValue()));
        pluginMappingInfo.remove(pluginId).forEach(this::unregisterMapping);
    }

    protected List<RequestMappingInfo> getMappings(String pluginId) {
        List<RequestMappingInfo> requestMappingInfos = pluginMappingInfo.get(pluginId);
        if (requestMappingInfos == null) {
            return Collections.emptyList();
        }
        return List.copyOf(requestMappingInfos);
    }

    protected RequestMappingInfo getPluginMappingForMethod(String pluginId, Method method, Class<?> handlerType) {
        RequestMappingInfo info = super.getMappingForMethod(method, handlerType);
        if (info != null) {
            ApiVersion apiVersion = handlerType.getAnnotation(ApiVersion.class);
            if (apiVersion == null) {
                return info;
            }
            info = RequestMappingInfo.paths(buildPrefix(pluginId, apiVersion.value()))
                    .build()
                    .combine(info);
        }
        return info;
    }

    protected String buildPrefix(String pluginName, String apiVersion) {
        GroupVersion groupVersion = GroupVersion.parseAPIVersion(apiVersion);
        if (StringUtils.hasText(groupVersion.group())) {

            return String.format("/apis/%s/%s", groupVersion.group(), groupVersion.version());
        }

        return String.format("/apis/api.plugin.pudans.tolim.cn/%s/plugins/%s", groupVersion.version(), pluginName);
    }
}
