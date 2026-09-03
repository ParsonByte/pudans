package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonParseException;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Data;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;


@Data
public class SystemState {
    public static final String SYSTEM_STATES_CONFIGMAP = "system-states";

    static final String GROUP = "states";

    private Boolean isSetup;


    private String setupUsername;


    public static SystemState deserialize(ConfigMap configMap) {
        Map<String, String> data = configMap.getData();
        if (data == null) {
            return new SystemState();
        }
        return JsonUtils.jsonToObject(data.getOrDefault(GROUP, emptyJsonObject()), SystemState.class);
    }


    public static void update(SystemState systemState, ConfigMap configMap) {
        Map<String, String> data = configMap.getData();
        if (data == null) {
            data = new LinkedHashMap<>();
            configMap.setData(data);
        }
        JsonNode modifiedJson = JsonUtils.mapper().convertValue(systemState, JsonNode.class);

        JsonNode sourceJson = JsonUtils.jsonToObject(data.getOrDefault(GROUP, emptyJsonObject()), JsonNode.class);
        try {

            JsonMergePatch jsonMergePatch = JsonMergePatch.fromJson(modifiedJson);

            JsonNode patchedNode = jsonMergePatch.apply(sourceJson);
            data.put(GROUP, JsonUtils.objectToJson(patchedNode));
        } catch (JsonPatchException e) {
            throw new JsonParseException(e);
        }
    }


    public static Mono<Void> upsetSystemState(ReactiveExtensionClient client, Consumer<SystemState> consumer) {
        return Mono.defer(() -> client.fetch(ConfigMap.class, SYSTEM_STATES_CONFIGMAP)
                        .switchIfEmpty(Mono.defer(() -> {
                            ConfigMap configMap = new ConfigMap();
                            configMap.setMetadata(new Metadata());
                            configMap.getMetadata().setName(SYSTEM_STATES_CONFIGMAP);
                            configMap.setData(new HashMap<>());
                            return client.create(configMap);
                        }))
                        .flatMap(configMap -> {
                            SystemState systemState = deserialize(configMap);
                            consumer.accept(systemState);
                            update(systemState, configMap);
                            return client.update(configMap);
                        }))
                .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                        .filter(t -> t instanceof OptimisticLockingFailureException

                                || t instanceof DuplicateNameException))
                .then();
    }

    private static String emptyJsonObject() {
        return "{}";
    }
}
