package cn.tolim.pudans.iam.core.message.sms;

import cn.tolim.pudans.iam.communal.enums.SmsType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;


@Getter
public class SmsMsgEvent extends ApplicationEvent {
    // Message type
    private final SmsType type;
    // Parameter
    private final Map<String, String> parameter;

    public SmsMsgEvent(SmsType type, Map<String, String> parameter) {
        super(parameter);
        this.type = type;
        this.parameter = parameter;
    }
}
