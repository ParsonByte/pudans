package cn.tolim.pudans.extension.exception;

import cn.tolim.pudans.extension.contract.GroupVersionKind;
import cn.tolim.pudans.extension.contract.exception.ExtensionException;
import org.openapi4j.core.validation.ValidationResults;
import org.springframework.http.HttpStatus;


public class SchemaViolationException extends ExtensionException {


    private final ValidationResults errors;

    // 保留校验明细供错误响应展示。
    public SchemaViolationException(GroupVersionKind gvk, ValidationResults errors) {
        super(HttpStatus.BAD_REQUEST, "数据结构校验失败：" + gvk, null, null, null);
        this.errors = errors;
    }

    public ValidationResults getErrors() {
        return errors;
    }
}
