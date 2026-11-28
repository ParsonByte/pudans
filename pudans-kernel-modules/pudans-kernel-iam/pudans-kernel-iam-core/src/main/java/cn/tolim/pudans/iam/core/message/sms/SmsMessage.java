package cn.tolim.pudans.iam.core.message.sms;

import cn.tolim.pudans.iam.communal.enums.SmsType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;


@Data
@NoArgsConstructor
public class SmsMessage implements Serializable {
    // Message type
    private SmsType type;
    // Parameter
    private Map<String, String> parameter;

    public SmsMessage(SmsType type, Map<String, String> parameter) {
        this.type = type;
        this.parameter = parameter;
    }
}
