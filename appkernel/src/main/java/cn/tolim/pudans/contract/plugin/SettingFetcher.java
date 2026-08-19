package cn.tolim.pudans.contract.plugin;

import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;


public interface SettingFetcher {

    <T> Optional<T> fetch(String group, Class<T> clazz);


    JsonNode getSettingValue(String group);


    Map<String, JsonNode> getSettingValues();
}
