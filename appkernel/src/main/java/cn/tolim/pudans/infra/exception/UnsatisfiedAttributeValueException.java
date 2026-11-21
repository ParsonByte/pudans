package cn.tolim.pudans.infra.exception;

import jakarta.validation.constraints.Null;
import org.jspecify.annotations.Nullable;
import org.springframework.web.server.ServerWebInputException;


public class UnsatisfiedAttributeValueException extends ServerWebInputException {

    // 创建属性值不满足约束的输入异常。
    public UnsatisfiedAttributeValueException(String reason) {
        super(reason);
    }

    // 附带问题详情码与参数，统一错误响应文案。
    public UnsatisfiedAttributeValueException(
            String reason, @Nullable String messageDetailCode, @Null Object[] messageDetailArguments) {
        super(reason, null, null, messageDetailCode, messageDetailArguments);
    }
}
