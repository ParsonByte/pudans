package cn.tolim.pudans.contract.plugin;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


@Getter
public class PluginConfigUpdatedEvent extends ApplicationEvent {


    private final Map<String, tools.jackson.databind.JsonNode> oldSettingValues;


    private final Map<String, tools.jackson.databind.JsonNode> newSettingValues;

    @Builder
    public PluginConfigUpdatedEvent(
            Object source,
            Map<String, tools.jackson.databind.JsonNode> oldSettingValues,
            Map<String, tools.jackson.databind.JsonNode> newSettingValues) {
        super(source);
        this.oldSettingValues = oldSettingValues;
        this.newSettingValues = newSettingValues;
    }
}
