package cn.tolim.pudans.infra.exception;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.Errors;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.BindErrorUtils;

public class RequestBodyValidationException extends ServerWebInputException {

    // 收集校验错误并写入问题详情扩展字段。
    public RequestBodyValidationException(Errors errors) {
        super("请求内容无效。");
        getBody().setProperty("errors", collectAllErrors(errors));
    }

    // 将消息可解析对象转换为最终错误文本。
    private static List<String> resolveErrors(List<? extends MessageSourceResolvable> errors) {
        return BindErrorUtils.resolve(errors).values().stream().toList();
    }

    // 合并全局与字段错误，保持原始顺序。
    private static List<String> collectAllErrors(Errors errors) {
        var globalErrors = resolveErrors(errors.getGlobalErrors());
        var fieldErrors = resolveErrors(errors.getFieldErrors());
        var result = new ArrayList<String>(globalErrors.size() + fieldErrors.size());
        result.addAll(globalErrors);
        result.addAll(fieldErrors);
        return result;
    }
}
