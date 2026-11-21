package cn.tolim.pudans.infra.utils;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import cn.tolim.pudans.setting.contract.Setting;
import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.controller.Reconciler.Result;
import cn.tolim.pudans.extension.contract.controller.RequeueException;
import cn.tolim.pudans.infra.contract.utils.JsonParseException;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public enum SettingUtils {
    ;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(v -> v.withValueInclusion(NON_NULL).withContentInclusion(NON_NULL))
            .build();

    private static final String VALUE_FIELD = "value";
    private static final String NAME_FIELD = "name";


    public static Map<String, String> settingDefinedDefaultValueMap(Setting setting) {
        List<Setting.SettingForm> forms = setting.getSpec().getForms();
        if (CollectionUtils.isEmpty(forms)) {
            return Map.of();
        }
        Map<String, String> data = new LinkedHashMap<>();
        for (Setting.SettingForm form : forms) {
            String group = form.getGroup();
            Map<String, JsonNode> groupValue = form.getFormSchema().stream()
                    .map(o -> MAPPER.convertValue(o, JsonNode.class))
                    .filter(jsonNode -> jsonNode.isObject() && jsonNode.has(NAME_FIELD) && jsonNode.has(VALUE_FIELD))
                    .map(jsonNode -> {
                        String name = jsonNode.get(NAME_FIELD).asString();
                        JsonNode value = jsonNode.get(VALUE_FIELD);
                        return Map.entry(name, value);
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            data.put(group, MAPPER.writeValueAsString(groupValue));
        }
        return data;
    }


    public static void createOrUpdateConfigMap(ExtensionClient client, String settingName, String configMapName) {
        Assert.notNull(client, "扩展客户端不能为空");
        Assert.hasText(settingName, "设置名称不能为空");
        Assert.hasText(configMapName, "ConfigMap 名称不能为空");

        client.fetch(Setting.class, settingName)
                .ifPresentOrElse(
                        setting -> {
                            final var source = SettingUtils.settingDefinedDefaultValueMap(setting);
                            client.fetch(ConfigMap.class, configMapName)
                                    .ifPresentOrElse(
                                            configMap -> {
                                                Map<String, String> modified =
                                                        Objects.requireNonNullElse(configMap.getData(), Map.of());
                                                var copy = new HashMap<>(modified);

                                                var merged = SettingUtils.mergePatch(modified, source);
                                                configMap.setData(merged);

                                                if (!Objects.equals(copy, configMap.getData())) {
                                                    client.update(configMap);
                                                }
                                            },
                                            () -> {
                                                ConfigMap configMap = new ConfigMap();
                                                configMap.setMetadata(new Metadata());
                                                configMap.getMetadata().setName(configMapName);
                                                configMap.setData(source);
                                                client.create(configMap);
                                            });
                        },
                        () -> {

                            throw new RequeueException(
                                    Result.requeue(null), "未找到设置 %s".formatted(settingName));
                        });
    }

    public static ConfigMap populateDefaultConfig(Setting setting, String configMapName) {
        var data = settingDefinedDefaultValueMap(setting);
        ConfigMap configMap = new ConfigMap();
        configMap.setMetadata(new Metadata());
        configMap.getMetadata().setName(configMapName);
        configMap.setData(data);
        return configMap;
    }


    public static Map<String, String> mergePatch(Map<String, String> modified, Map<String, String> source) {
        var modifiedJson = mapToJsonNode(modified);

        var sourceJson = mapToJsonNode(source);
        try {

            var jsonMergePatch = JsonMergePatch.fromJson(modifiedJson);

            var patchedNode = jsonMergePatch.apply(sourceJson);
            return jsonNodeToStringMap(patchedNode);
        } catch (JsonPatchException e) {
            throw new JsonParseException(e);
        }
    }


    public static ObjectNode settingConfigToJson(ConfigMap configMap) {
        if (configMap.getData() == null) {
            return MAPPER.createObjectNode();
        }
        return mapToObjectNode(configMap.getData());
    }


    public static Map<String, String> settingConfigJsonToMap(ObjectNode node) {
        return jsonNodeToStringMap(node);
    }


    private static com.fasterxml.jackson.databind.node.ObjectNode mapToJsonNode(Map<String, String> map) {
        var objectNode = JsonUtils.mapper().createObjectNode();
        map.forEach((k, v) -> {
            if (v == null) {
                objectNode.putNull(k);
                return;
            }
            try {
                var value = JsonUtils.mapper().readTree(v);
                objectNode.set(k, value);
            } catch (JsonProcessingException ignored) {

                objectNode.put(k, v);
            }
        });
        return objectNode;
    }

    private static ObjectNode mapToObjectNode(Map<String, String> map) {
        var objectNode = MAPPER.createObjectNode();
        map.forEach((k, v) -> {
            if (v == null) {
                objectNode.putNull(k);
                return;
            }
            try {
                var value = MAPPER.readTree(v);
                objectNode.set(k, value);
            } catch (JacksonException ignored) {

                objectNode.put(k, v);
            }
        });
        return objectNode;
    }

    private static Map<String, String> jsonNodeToStringMap(com.fasterxml.jackson.databind.JsonNode node) {
        Map<String, String> stringMap = new LinkedHashMap<>();
        node.forEachEntry((k, v) -> {
            if (v == null || v.isNull() || v.isMissingNode()) {
                stringMap.put(k, null);
                return;
            }
            if (v.isTextual()) {
                stringMap.put(k, v.asText());
                return;
            }
            if (v.isContainerNode()) {
                stringMap.put(k, v.toString());
                return;
            }
            stringMap.put(k, v.asText());
        });
        return stringMap;
    }

    private static Map<String, String> jsonNodeToStringMap(JsonNode node) {
        Map<String, String> stringMap = new LinkedHashMap<>();
        node.forEachEntry((k, v) -> {
            if (v == null || v.isNull() || v.isMissingNode()) {
                stringMap.put(k, null);
                return;
            }
            if (v.isString()) {
                stringMap.put(k, v.asString());
                return;
            }
            if (v.isContainer()) {
                stringMap.put(k, v.toString());
                return;
            }
            stringMap.put(k, v.asString());
        });
        return stringMap;
    }
}
