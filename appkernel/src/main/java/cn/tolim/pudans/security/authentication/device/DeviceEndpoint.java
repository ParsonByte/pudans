package cn.tolim.pudans.security.authentication.device;

import static cn.tolim.pudans.extension.contract.index.query.Queries.equal;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.security.contract.Device;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.router.selector.FieldSelector;
import cn.tolim.pudans.security.contract.device.DeviceSessions;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.security.Principal;
import java.util.Comparator;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class DeviceEndpoint implements CustomEndpoint {
    private final ReactiveExtensionClient client;
    private final ReactiveFindByIndexNameSessionRepository<?> sessionRepository;
    private final DeviceSessions deviceSessions;

    // 注册设备查询与撤销路由，并同步生成接口文档
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "DeviceV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET(
                        "/devices",
                        this::listDevices,
                        builder -> builder.operationId("ListDevices")
                                .description("List all user devices")
                                .tag(tag)
                                .response(responseBuilder().implementationArray(DeviceDto.class)))
                .DELETE(
                        "/devices/{deviceId}",
                        this::revokeDevice,
                        builder -> builder.operationId("RevokeDevice")
                                .description("Revoke a own device")
                                .tag(tag)
                                .parameter(parameterBuilder()
                                        .in(ParameterIn.PATH)
                                        .name("deviceId")
                                        .description("Device ID")
                                        .required(true))
                                .response(responseBuilder().responseCode(String.valueOf(HttpStatus.NO_CONTENT))))
                .build();
    }

    // 撤销前校验主体归属，避免越权操作他人设备
    private Mono<ServerResponse> revokeDevice(ServerRequest request) {
        final var deviceId = request.pathVariable("deviceId");
        return principalName()
                .flatMap(principalName -> deviceSessions.revoke(principalName, deviceId))
                .then(ServerResponse.noContent().build());
    }

    // 合并会话状态，标记当前设备与是否仍然活跃
    private Mono<ServerResponse> listDevices(ServerRequest request) {
        return getRequestContext(request)
                .flatMapMany(context -> {
                    var listOptions = new ListOptions();
                    var query = equal("spec.principalName", context.username());
                    listOptions.setFieldSelector(FieldSelector.of(query));
                    return client.listAll(Device.class, listOptions, Sort.by("metadata.creationTimestamp"))
                            .map(device -> {
                                var sessionId = device.getSpec().getSessionId();
                                var session = context.sessionMap().get(sessionId);
                                if (session != null) {
                                    device.getSpec().setLastAccessedTime(session.getLastAccessedTime());
                                }
                                return new DeviceDto()
                                        .setDevice(device)
                                        .setCurrentDevice(context.sessionId().equals(sessionId))
                                        .setActive(session != null && !session.isExpired());
                            })
                            .sort(deviceDtoComparator());
                })
                .collectList()
                .flatMap(deviceDto -> ServerResponse.ok().bodyValue(deviceDto));
    }

    // 当前设备与活跃设备优先，其余按最近访问倒序
    Comparator<DeviceDto> deviceDtoComparator() {
        return Comparator.comparing(DeviceDto::isCurrentDevice)
                .thenComparing(DeviceDto::isActive)
                .thenComparing(DeviceDto::getDevice, Comparator.comparing(device -> {
                    var accessedTime = device.getSpec().getLastAccessedTime();
                    return accessedTime == null ? device.getMetadata().getCreationTimestamp() : accessedTime;
                }))
                .reversed();
    }

    // 同时获取主体会话映射与当前会话，供状态比对
    private Mono<RequestContext> getRequestContext(ServerRequest request) {
        return principalName().flatMap(principalName -> {
            var builder = RequestContext.builder().sessionMap(Map.of()).username(principalName);
            var sessionMapMono =
                    sessionRepository.findByPrincipalName(principalName).doOnNext(builder::sessionMap);
            var sessionMono = request.exchange().getSession().doOnNext(session -> builder.sessionId(session.getId()));
            return Mono.when(sessionMapMono, sessionMono).then(Mono.fromSupplier(builder::build));
        });
    }

    // 从安全上下文提取当前登录主体名称
    Mono<String> principalName() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Principal::getName);
    }

    // 复用统一分组版本，保证接口路径前缀一致
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    @Builder
    record RequestContext(String username, String sessionId, Map<String, ? extends Session> sessionMap) {}

    @Data
    @Accessors(chain = true)
    @Schema(name = "UserDevice")
    static class DeviceDto {

        @Schema(requiredMode = REQUIRED)
        boolean currentDevice;
        @Schema(requiredMode = REQUIRED)
        boolean active;
        @Schema(requiredMode = REQUIRED)
        private Device device;
    }
}