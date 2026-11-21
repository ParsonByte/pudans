package cn.tolim.pudans.infra.setting;

import java.util.Map;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class ParameterSettingChangedEvent extends ApplicationEvent {


    @Getter
    private final Map<String, String> oldData;


    @Getter
    private final Map<String, String> newData;

    // 保存变更前后的不可变配置快照。
    public ParameterSettingChangedEvent(Object source, Map<String, String> oldData, Map<String, String> newData) {
        super(source);
        this.oldData = Map.copyOf(oldData);
        this.newData = Map.copyOf(newData);
    }
}
