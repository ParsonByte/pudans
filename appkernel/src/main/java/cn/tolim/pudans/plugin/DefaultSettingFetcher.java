package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.plugin.ReactiveSettingFetcher;
import cn.tolim.pudans.contract.plugin.SettingFetcher;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;


class DefaultSettingFetcher implements SettingFetcher {
    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;
    private final ReactiveSettingFetcher delegateFetcher;

    public DefaultSettingFetcher(ReactiveSettingFetcher reactiveSettingFetcher) {
        this.delegateFetcher = reactiveSettingFetcher;
    }

    @Override
    public <T> Optional<T> fetch(String group, Class<T> clazz) {
        return delegateFetcher.fetch(group, clazz).blockOptional(BLOCKING_TIMEOUT);
    }

    @Override
    public tools.jackson.databind.JsonNode getSettingValue(String group) {
        return delegateFetcher.getSettingValue(group).block(BLOCKING_TIMEOUT);
    }


    @Override
    public Map<String, tools.jackson.databind.JsonNode> getSettingValues() {
        return delegateFetcher.getSettingValues().block(BLOCKING_TIMEOUT);
    }
}
