package cn.tolim.pudans.extension.router;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.ListResult;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.extension.contract.router.SortableRequest;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

public class ExtensionRouterFunctionFactory {

    private final Scheme scheme;

    private final ReactiveExtensionClient client;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    // 保存方案、客户端与内建实体策略。
    public ExtensionRouterFunctionFactory(
            Scheme scheme, ReactiveExtensionClient client, BuiltinEntityPolicy builtinEntityPolicy) {
        this.scheme = scheme;
        this.client = client;
        this.builtinEntityPolicy = builtinEntityPolicy;
    }

    // 装配各处理器并生成带文档的路由。
    public RouterFunction<ServerResponse> create() {
        var getHandler = new ExtensionGetHandler(scheme, client);
        var listHandler = new ExtensionListHandler(scheme, client);
        var createHandler = new ExtensionCreateHandler(scheme, client, builtinEntityPolicy);
        var updateHandler = new ExtensionUpdateHandler(scheme, client, builtinEntityPolicy);
        var deleteHandler = new ExtensionDeleteHandler(scheme, client, builtinEntityPolicy);
        var patchHandler = new ExtensionPatchHandler(scheme, client, builtinEntityPolicy);

        var gvk = scheme.groupVersionKind();
        var kind = gvk.kind();
        var tagName = gvk.kind() + StringUtils.capitalize(gvk.version());
        return SpringdocRouteBuilder.route()
                .GET(
                        getHandler.pathPattern(),
                        getHandler,
                        builder -> builder.operationId("get" + kind)
                                .description("Get " + kind)
                                .tag(tagName)
                                .parameter(parameterBuilder()
                                        .in(ParameterIn.PATH)
                                        .name("name")
                                        .description("Name of " + scheme.singular()))
                                .response(responseBuilder()
                                        .responseCode("200")
                                        .description("Response single " + scheme.singular())
                                        .implementation(scheme.type())))
                .GET(listHandler.pathPattern(), listHandler, builder -> {
                    builder.operationId("list" + kind)
                            .description("List " + kind)
                            .tag(tagName)
                            .response(responseBuilder()
                                    .responseCode("200")
                                    .description("Response " + scheme.plural())
                                    .implementation(ListResult.generateGenericClass(scheme)));
                    SortableRequest.buildParameters(builder);
                })
                .POST(
                        createHandler.pathPattern(),
                        createHandler,
                        builder -> builder.operationId("create" + kind)
                                .description("Create " + kind)
                                .tag(tagName)
                                .requestBody(requestBodyBuilder()
                                        .description("Fresh " + scheme.singular())
                                        .implementation(scheme.type()))
                                .response(responseBuilder()
                                        .responseCode("200")
                                        .description("Response " + scheme.plural() + " created just now")
                                        .implementation(scheme.type())))
                .PUT(
                        updateHandler.pathPattern(),
                        updateHandler,
                        builder -> builder.operationId("update" + kind)
                                .description("Update " + kind)
                                .tag(tagName)
                                .parameter(parameterBuilder()
                                        .in(ParameterIn.PATH)
                                        .name("name")
                                        .description("Name of " + scheme.singular()))
                                .requestBody(requestBodyBuilder()
                                        .description("Updated " + scheme.singular())
                                        .implementation(scheme.type()))
                                .response(responseBuilder()
                                        .responseCode("200")
                                        .description("Response " + scheme.plural() + " updated just now")
                                        .implementation(scheme.type())))
                .PATCH(
                        patchHandler.pathPattern(),
                        patchHandler,
                        builder -> builder.operationId("patch" + kind)
                                .description("Patch " + kind)
                                .tag(tagName)
                                .parameter(parameterBuilder()
                                        .in(ParameterIn.PATH)
                                        .name("name")
                                        .description("Name of " + scheme.singular()))
                                .requestBody(requestBodyBuilder()
                                        .content(contentBuilder()
                                                .mediaType("application/json-patch+json")
                                                .schema(schemaBuilder()
                                                        .ref(RefUtils.constructRef(JsonPatch.SCHEMA_NAME)))))
                                .response(responseBuilder()
                                        .responseCode("200")
                                        .description("Response " + scheme.singular() + " patched just now")
                                        .implementation(scheme.type())))
                .DELETE(
                        deleteHandler.pathPattern(),
                        deleteHandler,
                        builder -> builder.operationId("delete" + kind)
                                .description("Delete " + kind)
                                .tag(tagName)
                                .parameter(parameterBuilder()
                                        .in(ParameterIn.PATH)
                                        .name("name")
                                        .description("Name of " + scheme.singular()))
                                .response(responseBuilder()
                                        .responseCode("200")
                                        .description("Response " + scheme.singular() + " deleted just now")))
                .build();
    }

    interface PathPatternGenerator {

        // 拼接版本与复数名生成资源路径。
        static String buildExtensionPathPattern(Scheme scheme) {
            var gvk = scheme.groupVersionKind();
            return ApiGroups.API_ROOT
                    + "/"
                    + gvk.version()
                    + "/"
                    + scheme.plural();
        }

        // 返回处理器负责的路径模板。
        String pathPattern();
    }

    interface GetHandler extends HandlerFunction<ServerResponse>, PathPatternGenerator {}

    interface ListHandler extends HandlerFunction<ServerResponse>, PathPatternGenerator {}

    interface CreateHandler extends HandlerFunction<ServerResponse>, PathPatternGenerator {}

    interface UpdateHandler extends HandlerFunction<ServerResponse>, PathPatternGenerator {}

    interface DeleteHandler extends HandlerFunction<ServerResponse>, PathPatternGenerator {}

    interface PatchHandler extends HandlerFunction<ServerResponse>, PathPatternGenerator {}
}
