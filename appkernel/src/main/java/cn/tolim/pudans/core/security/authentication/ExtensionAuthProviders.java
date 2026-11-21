package cn.tolim.pudans.core.security.authentication;

import cn.tolim.pudans.contract.security.AuthProvider;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.index.query.Queries;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.user.Connections;
import cn.tolim.pudans.core.infra.setting.ParameterSettingFetcher;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;


@Component
@RequiredArgsConstructor
public class ExtensionAuthProviders implements AuthProviders {
    private final ReactiveExtensionClient client;
    private final ObjectProvider<ParameterSettingFetcher> environmentFetcherProvider;
    private final Connections connections;

    private static boolean supportsBinding(AuthProvider authProvider) {
        return BooleanUtils.TRUE.equals(
                MetadataUtil.nullSafeLabels(authProvider).get(AuthProvider.AUTH_BINDING_LABEL));
    }

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

    @Override
    public Mono<AuthProvider> enable(String name) {
        return client.get(AuthProvider.class, name)
                .flatMap(authProvider -> updateAuthProviderEnabled(name, true).thenReturn(authProvider));
    }

    @Override
    public Mono<AuthProvider> disable(String name) {
        return client.get(AuthProvider.class, name)

                .filter(authProvider -> !AuthProviderUtils.isPrivileged(authProvider))
                .flatMap(authProvider -> updateAuthProviderEnabled(name, false).thenReturn(authProvider));
    }

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
                            })
                            .sorted(Comparator.comparingInt(ListedAuthProvider::getPriority)
                                    .thenComparing(ListedAuthProvider::getName))
                            .toList();
                });
    }

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

    private Mono<List<ParameterSetting.AuthProviderState>> fetchProviderStates() {
        return getParameterSettingsConfigMap()
                .map(ExtensionAuthProviders::getAuthProviderConfig)
                .map(ParameterSetting.AuthProvider::getStates)
                .defaultIfEmpty(List.of());
    }

    Flux<UserConnection> listMyConnections() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getName())
                .flatMapMany(connections::listConnections);
    }

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

        public String getName() {
            return authProvider.getMetadata().getName();
        }

        @Override
        public int compareTo(AuthProviderWithPriority o) {
            return Comparator.comparingInt(AuthProviderWithPriority::getPriority)
                    .thenComparing(AuthProviderWithPriority::getName)
                    .compare(this, o);
        }
    }
}
