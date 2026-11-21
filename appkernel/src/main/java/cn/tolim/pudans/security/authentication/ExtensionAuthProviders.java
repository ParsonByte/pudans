package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.security.contract.AuthProvider;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.ExtensionUtil;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.index.query.Queries;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.core.user.Connections;
import cn.tolim.pudans.infra.setting.ParameterSettingFetcher;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;


@Component
@RequiredArgsConstructor
public class ExtensionAuthProviders implements AuthProviders {

    private static final String FEISHU_PROVIDER = "feishu";

    private final ReactiveExtensionClient client;
    private final ObjectProvider<ParameterSettingFetcher> environmentFetcherProvider;
    private final Connections connections;
    private final SecurityParameters securitySettings;

    // 依据标签判断提供方是否支持账号绑定。
    private static boolean supportsBinding(AuthProvider authProvider) {
        return BooleanUtils.TRUE.equals(
                MetadataUtil.nullSafeLabels(authProvider).get(AuthProvider.AUTH_BINDING_LABEL));
    }

    // 读取或初始化认证参数配置，兼容缺失的分组与状态。
    private static ParameterSetting.AuthProvider getAuthProviderConfig(ConfigMap configMap) {
        if (configMap.getData() == null) {
            configMap.setData(new HashMap<>());
        }
        final Map<String, String> data = configMap.getData();
        String providerGroup = data.get(ParameterSetting.AuthProvider.GROUP);

        ParameterSetting.AuthProvider authProvider;
        if (StringUtils.isBlank(providerGroup)) {
            authProvider = new ParameterSetting.AuthProvider();
        } else {
            authProvider = JsonUtils.jsonToObject(providerGroup, ParameterSetting.AuthProvider.class);
        }
        if (authProvider.getStates() == null) {
            authProvider.setStates(new ArrayList<>());
        }

        return authProvider;
    }

    // 启用的前提是提供方具备可用的认证配置。
    @Override
    public Mono<AuthProvider> enable(String name) {
        return client.get(AuthProvider.class, name)
                .flatMap(authProvider -> isAvailable(authProvider)
                        ? updateAuthProviderEnabled(name, true).thenReturn(authProvider)
                        : Mono.error(new ServerWebInputException(
                                "登录提供方 [" + name + "] 没有可用的认证配置。")));
    }

    // 表单登录与已自动配置的提供方视为直接可用。
    private boolean isAvailable(AuthProvider authProvider) {
        if (AuthProvider.AuthType.FORM.equals(authProvider.getSpec().getAuthType())) {
            return true;
        }
        if (isAutoConfigured(authProvider.getMetadata().getName())) {
            return true;
        }
        var authenticationUrl = authProvider.getSpec().getAuthenticationUrl();
        return StringUtils.isNotBlank(authenticationUrl)
                && !authenticationUrl.startsWith("/oauth2/authorization/");
    }

    // 判断提供方是否由安全参数自动装配，无需手工启停。
    private boolean isAutoConfigured(String name) {
        if (FEISHU_PROVIDER.equals(name)) {
            return securitySettings.feishu().isConfigured();
        }
        return securitySettings.oauth2Providers().stream()
                        .anyMatch(provider -> name.equals(provider.getRegistrationId()))
                || securitySettings.oidcProviders().stream()
                        .anyMatch(provider -> name.equals(provider.getRegistrationId()));
    }

    // 特权或参数托管的提供方不允许在此停用。
    @Override
    public Mono<AuthProvider> disable(String name) {
        return client.get(AuthProvider.class, name)

                .filter(authProvider -> !AuthProviderUtils.isPrivileged(authProvider))
                .flatMap(authProvider -> isAutoConfigured(name)
                        ? Mono.<AuthProvider>error(new ServerWebInputException(
                                "登录方式 [" + name + "] 由认证参数控制，请通过参数设置停用。"))
                        : updateAuthProviderEnabled(name, false).thenReturn(authProvider));
    }

    // 合并提供方配置、绑定关系与启停状态，按优先级排序。
    @Override
    public Mono<List<ListedAuthProvider>> listAll() {
        var listOptions =
                ListOptions.builder().andQuery(ExtensionUtil.notDeleting()).build();
        var allProvidersMono = client.listAll(AuthProvider.class, listOptions, ExtensionUtil.defaultSort())
                .map(this::convertTo)
                .collectList();

        var boundProvidersMono = listMyConnections()
                .map(connection -> connection.getSpec().getRegistrationId())
                .collect(Collectors.toSet());

        return Mono.zip(allProvidersMono, boundProvidersMono, fetchProviderStates())
                .map(tuple3 -> {
                    var allProviders = tuple3.getT1();
                    var boundProviderNames = tuple3.getT2();
                    var stateMap = tuple3.getT3().stream()
                            .collect(Collectors.toMap(ParameterSetting.AuthProviderState::getName, Function.identity()));
                    return allProviders.stream()
                            .peek(authProvider -> {
                                authProvider.setIsBound(boundProviderNames.contains(authProvider.getName()));
                                authProvider.setEnabled(false);

                                var state = stateMap.get(authProvider.getName());
                                if (state != null) {
                                    authProvider.setEnabled(state.isEnabled());
                                    authProvider.setPriority(state.getPriority());
                                }
                                if (isAutoConfigured(authProvider.getName())) {
                                    authProvider.setEnabled(true);
                                }
                            })
                            .sorted(Comparator.comparingInt(ListedAuthProvider::getPriority)
                                    .thenComparing(ListedAuthProvider::getName))
                            .toList();
                });
    }

    // 仅返回已启用的提供方，并按配置的优先级排序。
    @Override
    public Flux<AuthProvider> getEnabledProviders() {
        return fetchProviderStates().flatMapMany(states -> {
            var namePriorityMap = states.stream()

                    .filter(ParameterSetting.AuthProviderState::isEnabled)
                    .collect(Collectors.toMap(
                            ParameterSetting.AuthProviderState::getName, ParameterSetting.AuthProviderState::getPriority));

            var listOptions = ListOptions.builder()
                    .andQuery(Queries.in("metadata.name", namePriorityMap.keySet()))
                    .andQuery(ExtensionUtil.notDeleting())
                    .build();
            return client.listAll(AuthProvider.class, listOptions, ExtensionUtil.defaultSort())
                    .map(provider -> new AuthProviderWithPriority()
                            .setAuthProvider(provider)
                            .setPriority(namePriorityMap.getOrDefault(
                                    provider.getMetadata().getName(), 0)))
                    .sort(AuthProviderWithPriority::compareTo)
                    .map(AuthProviderWithPriority::getAuthProvider);
        });
    }

    // 读取提供方启停状态，配置缺失时返回空列表。
    private Mono<List<ParameterSetting.AuthProviderState>> fetchProviderStates() {
        return getParameterSettingsConfigMap()
                .map(ExtensionAuthProviders::getAuthProviderConfig)
                .map(ParameterSetting.AuthProvider::getStates)
                .defaultIfEmpty(List.of());
    }

    // 列出当前登录用户已绑定的外部账号连接。
    Flux<UserConnection> listMyConnections() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getName())
                .flatMapMany(connections::listConnections);
    }

    // 转换为列表视图，绑定与启用状态留待汇总时填充。
    private ListedAuthProvider convertTo(AuthProvider authProvider) {
        return ListedAuthProvider.builder()
                .name(authProvider.getMetadata().getName())
                .displayName(authProvider.getSpec().getDisplayName())
                .logo(authProvider.getSpec().getLogo())
                .website(authProvider.getSpec().getWebsite())
                .description(authProvider.getSpec().getDescription())
                .authenticationUrl(authProvider.getSpec().getAuthenticationUrl())
                .helpPage(authProvider.getSpec().getHelpPage())
                .bindingUrl(authProvider.getSpec().getBindingUrl())
                .unbindingUrl(authProvider.getSpec().getUnbindUrl())
                .supportsBinding(supportsBinding(authProvider))
                .authType(authProvider.getSpec().getAuthType())
                .isBound(false)
                .enabled(false)
                .privileged(AuthProviderUtils.isPrivileged(authProvider))
                .build();
    }

    // 更新启停状态并写回配置，乐观锁冲突时自动重试。
    private Mono<ConfigMap> updateAuthProviderEnabled(String name, boolean enabled) {
        return Mono.defer(() -> getParameterSettingsConfigMap().flatMap(configMap -> {
                    var providerConfig = getAuthProviderConfig(configMap);
                    var stateToFoundOpt = providerConfig.getStates().stream()
                            .filter(state -> state.getName().equals(name))
                            .findFirst();
                    if (stateToFoundOpt.isEmpty()) {
                        var state = new ParameterSetting.AuthProviderState()
                                .setName(name)
                                .setEnabled(enabled);
                        providerConfig.getStates().add(state);
                    } else {
                        stateToFoundOpt.get().setEnabled(enabled);
                    }

                    configMap.getData().put(ParameterSetting.AuthProvider.GROUP, JsonUtils.objectToJson(providerConfig));

                    return client.update(configMap);
                }))
                .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                        .filter(OptimisticLockingFailureException.class::isInstance));
    }

    // 获取系统级参数配置，用于读写提供方状态。
    private Mono<ConfigMap> getParameterSettingsConfigMap() {
        var systemFetcher = environmentFetcherProvider.getIfUnique();
        if (systemFetcher == null) {
            return Mono.error(new IllegalStateException("未找到 ParameterSettingFetcher"));
        }
        return systemFetcher.getConfigMap();
    }

    @Data
    @Accessors(chain = true)
    static class AuthProviderWithPriority implements Comparable<AuthProviderWithPriority> {
        private AuthProvider authProvider;
        private int priority;

        // 名称取自元数据，供优先级相同时排序使用。
        public String getName() {
            return authProvider.getMetadata().getName();
        }

        // 先按优先级再按名称排序，保证顺序稳定。
        @Override
        public int compareTo(AuthProviderWithPriority o) {
            return Comparator.comparingInt(AuthProviderWithPriority::getPriority)
                    .thenComparing(AuthProviderWithPriority::getName)
                    .compare(this, o);
        }
    }
}
