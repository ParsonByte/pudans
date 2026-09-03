package cn.tolim.pudans.setting.endpoint;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.AnnotationSetting;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.index.query.Condition;
import cn.tolim.pudans.contract.extension.index.query.Queries;
import cn.tolim.pudans.plugin.PluginConst;
import cn.tolim.pudans.plugin.PluginService;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.apiresponse.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
class AnnotationSettingEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    private final PluginService pluginService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "AnnotationSettingV1AlphaUc";
        return SpringdocRouteBuilder.route()
                .GET(
                        "/annotationsettings",
                        this::listAvailableAnnotationSettings,
                        builder -> builder.operationId("listAvailableAnnotationSettings")
                                .description("""
                                        List available AnnotationSettings for the given targetRef. \
                                        The available AnnotationSettings are determined by the started plugins.""")
                                .tag(tag)
                                .parameter(parameterBuilder()
                                        .name("targetRef")
                                        .in(ParameterIn.QUERY)
                                        .description(
                                                "The targetRef of the AnnotationSetting. e.g.: 'content.pudans.tolim"
                                                        + ".cn/Post")
                                        .required(true)
                                        .implementation(String.class))
                                .response(Builder.responseBuilder().implementationArray(AnnotationSetting.class)))
                .build();
    }

    private Mono<ServerResponse> listAvailableAnnotationSettings(ServerRequest serverRequest) {
        var targetRef = serverRequest
                .queryParam("targetRef")
                .filter(StringUtils::hasText)
                .orElse(null);
        if (targetRef == null) {
            return Mono.error(new ServerWebInputException("Query param 'targetRef' is required"));
        }
        var getStartedPlugins = pluginService.getStartedPluginNames().collectList();
        var annotationSettings = getStartedPlugins.flatMapMany(pluginNames -> {
            Condition labelConditions = null;
            if (!CollectionUtils.isEmpty(pluginNames)) {
                var pluginLabelCondition = Queries.labelIn(PluginConst.PLUGIN_NAME_LABEL_NAME, pluginNames);
                if (labelConditions == null) {
                    labelConditions = pluginLabelCondition;
                } else {
                    labelConditions = labelConditions.or(pluginLabelCondition);
                }
            }
            if (labelConditions == null) {
                labelConditions = Queries.empty();
            }
            var builder = ListOptions.builder()
                    .andQuery(labelConditions)
                    .andQuery(Queries.equal("spec.targetRef", targetRef));
            return client.listAll(AnnotationSetting.class, builder.build(), defaultSort());
        });
        return ServerResponse.ok().body(annotationSettings, AnnotationSetting.class);
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.UC + "/v1alpha1");
    }
}
