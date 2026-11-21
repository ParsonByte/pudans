package cn.tolim.pudans.core.extension.exception;

import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.exception.ExtensionException;
import org.openapi4j.core.validation.ValidationResults;
import org.springframework.http.HttpStatus;


public class SchemaViolationException extends ExtensionException {


    private final ValidationResults errors;

    public SchemaViolationException(GroupVersionKind gvk, ValidationResults errors) {
        super(HttpStatus.BAD_REQUEST, "数据结构校验失败：" + gvk, null, null, null);
        this.errors = errors;
    }

    public ValidationResults getErrors() {
        return errors;
    }
}
