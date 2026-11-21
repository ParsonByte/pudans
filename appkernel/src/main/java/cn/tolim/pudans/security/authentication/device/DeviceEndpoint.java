package cn.tolim.pudans.security.authentication.device;

import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.security.resource.Device;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.router.selector.FieldSelector;
import cn.tolim.pudans.contract.security.device.DeviceSessions;
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

    private Mono<ServerResponse> revokeDevice(ServerRequest request) {
        final var deviceId = request.pathVariable("deviceId");
        return principalName()
                .flatMap(principalName -> deviceSessions.revoke(principalName, deviceId))
                .then(ServerResponse.noContent().build());
    }

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

    Comparator<DeviceDto> deviceDtoComparator() {
        return Comparator.comparing(DeviceDto::isCurrentDevice)
                .thenComparing(DeviceDto::isActive)
                .thenComparing(DeviceDto::getDevice, Comparator.comparing(device -> {
                    var accessedTime = device.getSpec().getLastAccessedTime();
                    return accessedTime == null ? device.getMetadata().getCreationTimestamp() : accessedTime;
                }))
                .reversed();
    }

    private Mono<RequestContext> getRequestContext(ServerRequest request) {
        return principalName().flatMap(principalName -> {
            var builder = RequestContext.builder().sessionMap(Map.of()).username(principalName);
            var sessionMapMono =
                    sessionRepository.findByPrincipalName(principalName).doOnNext(builder::sessionMap);
            var sessionMono = request.exchange().getSession().doOnNext(session -> builder.sessionId(session.getId()));
            return Mono.when(sessionMapMono, sessionMono).then(Mono.fromSupplier(builder::build));
        });
    }

    Mono<String> principalName() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Principal::getName);
    }

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