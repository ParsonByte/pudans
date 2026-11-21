package cn.tolim.pudans.security.authentication.device;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.infra.utils.IpAddressUtils.getClientIp;
import static cn.tolim.pudans.security.authentication.rememberme.PersistentTokenBasedRememberMeServices.REMEMBER_ME_SERIES_REQUEST_NAME;

import cn.tolim.pudans.security.contract.Device;
import cn.tolim.pudans.extension.contract.ExtensionUtil;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.index.query.Queries;
import cn.tolim.pudans.security.contract.device.DeviceSessions;
import cn.tolim.pudans.infra.utils.UserAgentUtils;
import cn.tolim.pudans.security.authentication.rememberme.PersistentRememberMeTokenRepository;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ua_parser.UserAgent;
import cn.tolim.pudans.event.security.NewDeviceLoginEvent;

@Slf4j
@Component
@RequiredArgsConstructor
class ExtensionDeviceSessions implements DeviceSessions {
    private final ReactiveExtensionClient client;
    private final DeviceCookieResolver deviceCookieResolver;
    private final ReactiveSessionRepository<?> sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PersistentRememberMeTokenRepository rememberMeTokenRepository;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    // 读取客户端标识请求头，缺失时返回空值
    @Nullable
    private static String getUserAgent(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
    }

    // 已有设备则续用，否则创建并下发标识与登录事件
    @Override
    public Mono<Void> loginSuccess(ServerWebExchange exchange, Authentication authentication) {
        return updateExistingDevice(exchange, authentication)
                .switchIfEmpty(createDevice(exchange, authentication)
                        .flatMap(client::create)
                        .doOnNext(device -> {
                            deviceCookieResolver.setCookie(
                                    exchange, device.getMetadata().getName());
                            eventPublisher.publishEvent(new NewDeviceLoginEvent(this, device));
                        }))
                .then();
    }

    // 会话标识变化后同步设备，并删除旧会话
    @Override
    public Mono<Void> changeSessionId(ServerWebExchange exchange) {
        var deviceIdCookie = deviceCookieResolver.resolveCookie(exchange);
        if (deviceIdCookie == null) {
            return Mono.empty();
        }
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(trustResolver::isAuthenticated)
                .map(Principal::getName)
                .flatMap(username -> {
                    var deviceId = deviceIdCookie.getValue();
                    return updateWithRetry(deviceId, username, device -> {
                        var oldSessionId = device.getSpec().getSessionId();
                        return exchange.getSession()
                                .filter(session -> !session.getId().equals(oldSessionId))
                                .flatMap(session -> {
                                    device.getSpec().setSessionId(session.getId());
                                    device.getSpec().setLastAccessedTime(session.getLastAccessTime());
                                    Optional.ofNullable(exchange.getAttribute(REMEMBER_ME_SERIES_REQUEST_NAME))
                                            .filter(String.class::isInstance)
                                            .map(String.class::cast)
                                            .filter(Predicate.not(String::isBlank))
                                            .ifPresent(id ->
                                                    device.getSpec().setRememberMeSeriesId(id));
                                    return sessionRepository.deleteById(oldSessionId);
                                })
                                .thenReturn(device);
                    })
                            .then();
                });
    }

    // 主体不符直接撤销；冲突时退避重试乐观锁更新
    private Mono<Device> updateWithRetry(
            String deviceId, String username, Function<Device, Mono<Device>> updateFunction) {
        return Mono.defer(() -> client.fetch(Device.class, deviceId).flatMap(device -> {
                    if (!Objects.equals(username, device.getSpec().getPrincipalName())) {
                        log.debug(
                                "设备 {} 的主体名称不匹配，预期 {}，实际 {}，正在撤销设备",
                                deviceId,
                                username,
                                device.getSpec().getPrincipalName());
                        return doRevoke(device).then(Mono.empty());
                    }
                    return updateFunction.apply(device).flatMap(client::update);
                }))
                .retryWhen(Retry.backoff(8, Duration.ofMillis(100))
                        .filter(OptimisticLockingFailureException.class::isInstance));
    }

    // 校验客户端一致，刷新会话与最近登录时间
    private Mono<Device> updateExistingDevice(ServerWebExchange exchange, Authentication authentication) {
        var deviceIdCookie = deviceCookieResolver.resolveCookie(exchange);
        if (deviceIdCookie == null) {
            return Mono.empty();
        }
        var principalName = authentication.getName();
        return updateWithRetry(deviceIdCookie.getValue(), principalName, existingDevice -> {
            var userAgent = getUserAgent(exchange);
            var deviceUa = existingDevice.getSpec().getUserAgent();
            if (!Objects.equals(deviceUa, userAgent)) {

                log.debug(
                        "设备 {} 的 User-Agent 已变化，预期 {}，实际 {}，正在撤销设备",
                        existingDevice.getMetadata().getName(),
                        deviceUa,
                        userAgent);
                return doRevoke(existingDevice).then(Mono.empty());
            }
            var sessionId = existingDevice.getSpec().getSessionId();
            return exchange.getSession()
                    .delayUntil(session -> {
                        if (session.getId().equals(sessionId)) {
                            return Mono.empty();
                        }
                        log.debug(
                                "设备 {} 的会话 ID 已变化，正在删除旧会话 {}",
                                existingDevice.getMetadata().getName(),
                                sessionId);
                        return sessionRepository.deleteById(sessionId);
                    })
                    .doOnNext(session -> {
                        existingDevice.getSpec().setSessionId(session.getId());
                        existingDevice.getSpec().setLastAccessedTime(session.getLastAccessTime());
                        existingDevice.getSpec().setLastAuthenticatedTime(Instant.now());
                        existingDevice
                                .getSpec()
                                .setRememberMeSeriesId(exchange.getAttribute(REMEMBER_ME_SERIES_REQUEST_NAME));
                    })
                    .thenReturn(existingDevice);
        });
    }

    // 仅当设备属于该主体时才撤销，避免越权
    @Override
    public Mono<Void> revoke(String principalName, String deviceId) {
        return client.fetch(Device.class, deviceId)
                .filter(device -> device.getSpec().getPrincipalName().equals(principalName))
                .delayUntil(this::doRevoke)
                .then();
    }

    // 撤销该主体名下全部设备与其会话记录
    @Override
    public Mono<Void> revoke(String username) {
        var listOptions = ListOptions.builder()
                .andQuery(Queries.equal("spec.principalName", username))
                .build();
        return client.listAll(Device.class, listOptions, defaultSort())
                .delayUntil(this::doRevoke)
                .then();
    }

    // 按请求标识返回设备，已删除的记录视为不存在
    @Override
    public Mono<Device> resolveCurrentDevice(ServerWebExchange exchange) {
        var deviceIdCookie = deviceCookieResolver.resolveCookie(exchange);
        if (deviceIdCookie == null) {
            return Mono.empty();
        }
        var deviceId = deviceIdCookie.getValue();
        return client.fetch(Device.class, deviceId).filter(Predicate.not(ExtensionUtil::isDeleted));
    }

    // 依次清理记住我令牌、会话与设备记录，避免残留
    private Mono<Void> doRevoke(Device device) {
        return removeRememberMeToken(device)
                .then(sessionRepository.deleteById(device.getSpec().getSessionId()))
                .then(client.delete(device))
                .then();
    }

    // 设备绑定记住我序列时删除令牌，防止自动登录
    private Mono<Void> removeRememberMeToken(Device device) {
        var seriesId = device.getSpec().getRememberMeSeriesId();
        if (StringUtils.isBlank(seriesId)) {
            return Mono.empty();
        }
        log.debug(
                "正在删除序列 ID 为 {} 的记住我令牌（设备 {}）",
                seriesId,
                device.getMetadata().getName());
        return rememberMeTokenRepository.removeToken(seriesId);
    }

    // 根据请求构建设备元数据，并绑定当前会话
    Mono<Device> createDevice(ServerWebExchange exchange, Authentication authentication) {
        Assert.notNull(authentication, "认证信息不能为空。");
        return Mono.fromSupplier(() -> {
                    var device = new Device();
                    device.setMetadata(new Metadata());
                    device.getMetadata().setName(generateDeviceId());

                    var userAgent = getUserAgent(exchange);
                    var deviceInfo = DeviceInfo.parse(userAgent);
                    device.setSpec(new Device.Spec()
                            .setUserAgent(userAgent)
                            .setPrincipalName(authentication.getName())
                            .setLastAuthenticatedTime(Instant.now())
                            .setIpAddress(getClientIp(exchange.getRequest()))
                            .setRememberMeSeriesId(exchange.getAttribute(REMEMBER_ME_SERIES_REQUEST_NAME)));
                    device.getStatus().setOs(deviceInfo.os()).setBrowser(deviceInfo.browser());
                    return device;
                })
                .flatMap(device -> exchange.getSession()
                        .doOnNext(session -> {
                            device.getSpec().setSessionId(session.getId());
                            device.getSpec().setLastAccessedTime(session.getLastAccessTime());
                        })
                        .thenReturn(device));
    }

    // 生成无连字符标识，便于直接作为资源名称
    String generateDeviceId() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }

    record DeviceInfo(String browser, String os) {

        // 合并浏览器与系统版本，生成可读的设备描述
        public static DeviceInfo parse(String agentString) {
            var client = UserAgentUtils.parse(agentString);
            UserAgent ua = client.userAgent;
            var browserVersion = Stream.of(ua.major, ua.minor, ua.patch)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining("."));
            var browserString = ua.family + (browserVersion.isEmpty() ? "" : " " + browserVersion);

            var os = client.os;
            var osVersion = Stream.of(os.major, os.minor, os.patch, os.patchMinor)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining("."));
            var osString = os.family + (osVersion.isEmpty() ? "" : " " + osVersion);
            return new DeviceInfo(browserString, osString);
        }
    }
}
