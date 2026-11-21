package cn.tolim.pudans.core.infra.exception;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.Errors;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.BindErrorUtils;

public class RequestBodyValidationException extends ServerWebInputException {

    public RequestBodyValidationException(Errors errors) {
        super("请求内容无效。");
        getBody().setProperty("errors", collectAllErrors(errors));
    }

    private static List<String> resolveErrors(List<? extends MessageSourceResolvable> errors) {
        return BindErrorUtils.resolve(errors).values().stream().toList();
    }

    private static List<String> collectAllErrors(Errors errors) {
        var globalErrors = resolveErrors(errors.getGlobalErrors());
        var fieldErrors = resolveErrors(errors.getFieldErrors());
        var result = new ArrayList<String>(globalErrors.size() + fieldErrors.size());
        result.addAll(globalErrors);
        result.addAll(fieldErrors);
        return result;
    }
}
