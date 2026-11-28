package cn.tolim.pudans.iam.core.message.mail;

import cn.tolim.pudans.iam.communal.enums.MailType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;


@Getter
public class MailMsgEvent extends ApplicationEvent {
    // Message type
    private final MailType type;
    // Recipient
    private final String receiver;
    // Parameter
    private final Map<String, Object> parameter;

    public MailMsgEvent(MailType type, String receiver, Map<String, Object> parameter) {
        super(type);
        this.type = type;
        this.receiver = receiver;
        this.parameter = parameter;
    }
}
