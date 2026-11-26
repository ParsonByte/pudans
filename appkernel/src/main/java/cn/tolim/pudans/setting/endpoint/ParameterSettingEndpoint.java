package cn.tolim.pudans.setting.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.infra.contract.ValidationUtils;
import cn.tolim.pudans.infra.setting.MailSettings;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import cn.tolim.pudans.security.authentication.AuthenticationConnectionTester;
import cn.tolim.pudans.setting.SiteAssets;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class ParameterSettingEndpoint implements CustomEndpoint {

    public static final String RESTART_REQUIRED_HEADER = "X-Pudans-Restart-Required";

    private static final String GROUP_PARAMETER = "group";

    private final ParameterGroups parameterGroups;

    private final MailSettings mailSettings;

    private final AuthenticationConnectionTester connectionTester;

    private final SiteAssets siteAssets;

    // 注册参数读写与连通性测试等管理接口
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "ParameterSettingV1alpha1";
        return SpringdocRouteBuilder.route()
                .POST(
                        "/parameter-settings/authentication/-/test",
                        this::testAuthenticationConnection,
                        builder -> builder.operationId("TestAuthenticationConnection")
                                .description("Test an authentication provider configuration without saving it.")
                                .tag(tag)
                                .requestBody(requestBodyBuilder()
                                        .required(true)
                                        .implementation(
                                                AuthenticationConnectionTester.ConnectionTestRequest.class))
                                .response(responseBuilder()
                                        .implementation(
                                                AuthenticationConnectionTester.ConnectionTestResult.class)))
                .POST(
                        "/parameter-settings/mail/-/test",
                        this::sendTestMail,
                        builder -> builder.operationId("SendParameterSettingTestMail")
                                .description("Send a test email using the configured SMTP settings.")
                                .tag(tag)
                                .requestBody(requestBodyBuilder().required(true).implementation(TestMailRequest.class))
                                .response(responseBuilder()
                                        .responseCode(String.valueOf(HttpStatus.NO_CONTENT))
                                        .implementation(Void.class)))
                .POST(
                        "/parameter-settings/site/-/assets",
                        this::uploadSiteAsset,
                        builder -> builder.operationId("UploadParameterSettingSiteAsset")
                                .description("Upload a site image asset and return its absolute URL.")
                                .tag(tag)
                                .requestBody(requestBodyBuilder()
                                        .required(true)
                                        .content(contentBuilder()
                                                .mediaType(MediaType.MULTIPART_FORM_DATA_VALUE)))
                                .response(responseBuilder().implementation(SiteAssetView.class)))
                .GET(
                        "/parameter-settings/{group}",
                        this::getGroup,
                        builder -> builder.operationId("getParameterSettingByGroup")
                                .description("Get parameter settings by group. Sensitive fields are masked.")
                                .tag(tag)
                                .response(responseBuilder()
                                        .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE))
                                        .implementation(Object.class))
                                .parameter(groupParameter("Group of the parameter settings")))
                .PUT(
                        "/parameter-settings/{group}",
                        this::updateGroup,
                        builder -> builder.operationId("updateParameterSettingByGroup")
                                .description("Update parameter settings by group. "
                                        + "Send ******** for a sensitive field to keep the stored value.")
                                .tag(tag)
                                .parameter(groupParameter("Group of the parameter settings"))
                                .requestBody(requestBodyBuilder().implementation(Object.class))
                                .response(responseBuilder()
                                        .responseCode(String.valueOf(HttpStatus.NO_CONTENT))
                                        .implementation(Void.class)))
                .GET(
                        "/systemconfigs/{group}",
                        this::getLegacyGroup,
                        builder -> builder.operationId("getSystemConfigByGroup")
                                .description("Deprecated: use /parameter-settings/{group} instead.")
                                .deprecated(true)
                                .tag(tag)
                                .response(responseBuilder()
                                        .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE))
                                        .implementation(Object.class))
                                .parameter(groupParameter("Group of the system config")))
                .PUT(
                        "/systemconfigs/{group}",
                        this::updateLegacyGroup,
                        builder -> builder.operationId("updateSystemConfigByGroup")
                                .description("Deprecated: use /parameter-settings/{group} instead.")
                                .deprecated(true)
                                .tag(tag)
                                .parameter(groupParameter("Group of the system config"))
                                .requestBody(requestBodyBuilder().implementation(Object.class))
                                .response(responseBuilder()
                                        .responseCode(String.valueOf(HttpStatus.NO_CONTENT))
                                        .implementation(Void.class)))
                .build();
    }

    // 统一构造分组路径参数的文档描述
    private static org.springdoc.core.fn.builders.parameter.Builder groupParameter(String description) {
        return parameterBuilder()
                .in(ParameterIn.PATH)
                .name(GROUP_PARAMETER)
                .required(true)
                .description(description);
    }

    // 使用请求体配置试连，不持久化设置
    private Mono<ServerResponse> testAuthenticationConnection(ServerRequest request) {
        return request
                .bodyToMono(AuthenticationConnectionTester.ConnectionTestRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(connectionTester::test)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 校验收件地址后发送测试邮件
    private Mono<ServerResponse> sendTestMail(ServerRequest request) {
        return request.bodyToMono(TestMailRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> {
                    if (body.to() == null || !ValidationUtils.EMAIL_PATTERN.matcher(body.to()).matches()) {
                        return Mono.error(new ServerWebInputException("to 必须是有效的邮箱地址。"));
                    }
                    return mailSettings.sendTest(body.to()).then(ServerResponse.noContent().build());
                });
    }

    // 校验字段与文件后存储站点图片
    private Mono<ServerResponse> uploadSiteAsset(ServerRequest request) {
        return request.multipartData().flatMap(parts -> {
            var fieldPart = parts.getFirst("field");
            if (!(fieldPart instanceof FormFieldPart field) || !StringUtils.hasText(field.value())) {
                return Mono.error(new ServerWebInputException("需要名为 field 的 multipart 文本字段。"));
            }
            var filePart = parts.getFirst("file");
            if (!(filePart instanceof FilePart file)) {
                return Mono.error(new ServerWebInputException("需要名为 file 的 multipart 文件字段。"));
            }
            return siteAssets
                    .store(field.value(), file, request.exchange().getRequest())
                    .flatMap(url -> ServerResponse.ok().bodyValue(new SiteAssetView(url)));
        });
    }

    // 读取分组参数并按要求脱敏敏感字段
    private Mono<ServerResponse> getGroup(ServerRequest request) {
        var group = request.pathVariable(GROUP_PARAMETER);
        return parameterGroups.getGroup(group).flatMap(json -> ServerResponse.ok().bodyValue(json));
    }

    // 更新分组参数，敏感字段可保留原值
    private Mono<ServerResponse> updateGroup(ServerRequest request) {
        var group = request.pathVariable(GROUP_PARAMETER);
        return request.bodyToMono(ObjectNode.class)
                .switchIfEmpty(Mono.error(new org.springframework.web.server.ServerWebInputException(
                        "请求体不能为空。")))
                .flatMap(body -> parameterGroups.updateGroup(group, body))
                .flatMap(result -> noContent(result.restartRequired()));
    }

    // 兼容旧路径读取系统配置
    private Mono<ServerResponse> getLegacyGroup(ServerRequest request) {
        var group = request.pathVariable(GROUP_PARAMETER);
        return parameterGroups.getLegacyGroup(group).flatMap(json -> ServerResponse.ok().bodyValue(json));
    }

    // 兼容旧路径更新系统配置
    private Mono<ServerResponse> updateLegacyGroup(ServerRequest request) {
        var group = request.pathVariable(GROUP_PARAMETER);
        return request.bodyToMono(ObjectNode.class)
                .switchIfEmpty(Mono.error(new org.springframework.web.server.ServerWebInputException(
                        "请求体不能为空。")))
                .flatMap(body -> parameterGroups.updateLegacyGroup(group, body))
                .flatMap(result -> noContent(result.restartRequired()));
    }

    // 需要重启时通过响应头提示客户端
    private static Mono<ServerResponse> noContent(boolean restartRequired) {
        var response = ServerResponse.noContent();
        if (restartRequired) {
            response.header(RESTART_REQUIRED_HEADER, "true");
        }
        return response.build();
    }

    public record TestMailRequest(String to) {}

    public record SiteAssetView(String url) {}

    // 端点挂在核心 API 组下
    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(ApiGroups.API, ApiGroups.VERSION);
    }
}
