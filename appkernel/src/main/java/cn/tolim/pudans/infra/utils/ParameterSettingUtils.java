package cn.tolim.pudans.infra.utils;

import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;


@Slf4j
public enum ParameterSettingUtils {
    ;

    private static final ObjectMapper mapper = JsonUtils.mapper();

    private static final String DATA_SNAPSHOT_ANNO = "pudans.tolim.cn/data-snapshot";


    // 按配置组深度合并，覆盖值优先。
    public static Map<String, String> mergeMap(Map<String, String> defaultMap, Map<String, String> overrideMap)
            throws JsonProcessingException {
        if (CollectionUtils.isEmpty(defaultMap)) {
            return overrideMap;
        }
        if (CollectionUtils.isEmpty(overrideMap)) {
            return defaultMap;
        }
        var result = new HashMap<>(defaultMap);
        for (Map.Entry<String, String> entry : overrideMap.entrySet()) {
            var group = entry.getKey();
            var overrideJson = entry.getValue();
            if (result.containsKey(group)) {

                String defaultJson = result.get(group);
                result.put(group, mergeJsonStrings(defaultJson, overrideJson));
            } else {

                result.put(group, overrideJson);
            }
        }
        return result;
    }


    // 合并数据并保留覆盖方的元数据。
    public static ConfigMap mergeConfigMap(ConfigMap defaultConfigMap, ConfigMap overrideConfigMap)
            throws JsonProcessingException {
        var mergedData = mergeMap(
                defaultConfigMap.getData() != null ? defaultConfigMap.getData() : Map.of(),
                overrideConfigMap.getData() != null ? overrideConfigMap.getData() : Map.of());
        var merged = new ConfigMap();
        merged.setMetadata(overrideConfigMap.getMetadata());
        merged.setData(mergedData);
        return merged;
    }

    // 将两组 JSON 配置深度合并为文本。
    private static String mergeJsonStrings(String mainJson, String updateJson) throws JsonProcessingException {
        var mainNode = mapper.readTree(mainJson);
        var updateNode = mapper.readTree(updateJson);


        var mergedNode = deepMerge(mainNode, updateNode);

        return mapper.writeValueAsString(mergedNode);
    }

    // 对象递归合并，其余类型直接覆盖。
    private static JsonNode deepMerge(JsonNode mainNode, JsonNode updateNode) {

        if (!mainNode.isObject() || !updateNode.isObject()) {
            return updateNode;
        }
        var mainObject = (ObjectNode) mainNode;
        updateNode.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if (mainObject.has(key)) {
                mainObject.set(key, deepMerge(mainObject.get(key), value));
            } else {
                mainObject.set(key, value);
            }
        });
        return mainObject;
    }


    // 数据变化时更新校验和，返回是否需要同步。
    public static boolean populateChecksum(ConfigMap configMap) {
        var toHash =
                Optional.ofNullable(configMap.getData()).map(Objects::toString).orElse("");
        var checksum =
                Hashing.sha256().hashString(toHash, StandardCharsets.UTF_8).toString();
        var metadata = configMap.getMetadata();
        var notChanged = Optional.ofNullable(metadata.getAnnotations())
                .map(annotations -> annotations.get("checksum"))
                .stream()
                .anyMatch(existingChecksum -> Objects.equals(checksum, existingChecksum));
        if (notChanged) {
            log.debug("ConfigMap '{}' 未发生变化。", configMap.getMetadata().getName());
            return false;
        }
        log.debug(
                "ConfigMap '{}' 已发生变化，正在更新校验和 {}。",
                configMap.getMetadata().getName(),
                checksum);
        if (metadata.getAnnotations() == null) {
            metadata.setAnnotations(new HashMap<>());
        }
        metadata.getAnnotations().put("checksum", checksum);
        return true;
    }


    // 将当前数据快照写入注解供后续差异比较。
    public static void updateDataSnapshot(ConfigMap configMap) {
        Optional.ofNullable(configMap.getData()).map(JsonUtils::objectToJson).ifPresent(dataJson -> {
            var metadata = configMap.getMetadata();
            if (metadata.getAnnotations() == null) {
                metadata.setAnnotations(new HashMap<>());
            }
            metadata.getAnnotations().put(DATA_SNAPSHOT_ANNO, dataJson);
        });
    }


    // 读取数据快照，缺失时返回空映射。
    public static Map<String, String> getDataSnapshot(ConfigMap configMap) {
        return Optional.ofNullable(configMap.getMetadata().getAnnotations())
                .map(annotations -> annotations.get(DATA_SNAPSHOT_ANNO))
                .map(dataJson -> JsonUtils.jsonToObject(dataJson, new TypeReference<Map<String, String>>() {}))
                .orElse(Map.of());
    }
}
