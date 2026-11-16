package cn.tolim.pudans.setting.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.infra.setting.MailSettingsService;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ParameterSettingGroupService groupService;

    private final MailSettingsService mailSettingsService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "ParameterSettingV1alpha1Console";
        final var legacyTag = "SystemConfigV1alpha1Console";
        return SpringdocRouteBuilder.route()
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
                                .tag(legacyTag)
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
                                .tag(legacyTag)
                                .parameter(groupParameter("Group of the system config"))
                                .requestBody(requestBodyBuilder().implementation(Object.class))
                                .response(responseBuilder()
                                        .responseCode(String.valueOf(HttpStatus.NO_CONTENT))
                                        .implementation(Void.class)))
                .build();
    }

    private static org.springdoc.core.fn.builders.parameter.Builder groupParameter(String description) {
        return parameterBuilder()
                .in(ParameterIn.PATH)
                .name(GROUP_PARAMETER)
                .required(true)
                .description(description);
    }

    private Mono<ServerResponse> sendTestMail(ServerRequest request) {
        return request.bodyToMono(TestMailRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("Request body is required.")))
                .flatMap(body -> {
                    if (body.to() == null || !EMAIL_PATTERN.matcher(body.to()).matches()) {
                        return Mono.error(new ServerWebInputException("to must be a valid email address."));
                    }
                    return mailSettingsService.sendTest(body.to()).then(ServerResponse.noContent().build());
                });
    }

    private Mono<ServerResponse> getGroup(ServerRequest request) {
        var group = request.pathVariable(GROUP_PARAMETER);
        return groupService.getGroup(group).flatMap(json -> ServerResponse.ok().bodyValue(json));
    }

    private Mono<ServerResponse> updateGroup(ServerRequest request) {
        var group = request.pathVariable(GROUP_PARAMETER);
        return request.bodyToMono(ObjectNode.class)
                .switchIfEmpty(Mono.error(new org.springframework.web.server.ServerWebInputException(
                        "Request body is required.")))
                .flatMap(body -> groupService.updateGroup(group, body))
                .flatMap(result -> noContent(result.restartRequired()));
    }

    private Mono<ServerResponse> getLegacyGroup(ServerRequest request) {
        var group = request.pathVariable(GROUP_PARAMETER);
        return groupService.getLegacyGroup(group).flatMap(json -> ServerResponse.ok().bodyValue(json));
    }

    private Mono<ServerResponse> updateLegacyGroup(ServerRequest request) {
        var group = request.pathVariable(GROUP_PARAMETER);
        return request.bodyToMono(ObjectNode.class)
                .switchIfEmpty(Mono.error(new org.springframework.web.server.ServerWebInputException(
                        "Request body is required.")))
                .flatMap(body -> groupService.updateLegacyGroup(group, body))
                .flatMap(result -> noContent(result.restartRequired()));
    }

    private static Mono<ServerResponse> noContent(boolean restartRequired) {
        var response = ServerResponse.noContent();
        if (restartRequired) {
            response.header(RESTART_REQUIRED_HEADER, "true");
        }
        return response.build();
    }

    public record TestMailRequest(String to) {}

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(ApiGroups.API, ApiGroups.VERSION);
    }
}
