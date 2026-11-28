package cn.tolim.pudans.iam.core.message.mail;

import cn.tolim.pudans.iam.communal.enums.MailType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;


@Data
@NoArgsConstructor
public class MailMessage implements Serializable {

    // Message type
    private MailType type;

    // Recipient
    private String receiver;

    // Parameter
    private Map<String, Object> parameter;

    public MailMessage(MailType type, String receiver, Map<String, Object> parameter) {
        this.type = type;
        this.receiver = receiver;
        this.parameter = parameter;
    }
}
