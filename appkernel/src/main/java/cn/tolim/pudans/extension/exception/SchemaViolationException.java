package cn.tolim.pudans.extension.exception;

import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.exception.ExtensionException;
import org.openapi4j.core.validation.ValidationResults;
import org.springframework.http.HttpStatus;


public class SchemaViolationException extends ExtensionException {


    private final ValidationResults errors;

    public SchemaViolationException(GroupVersionKind gvk, ValidationResults errors) {
        super(HttpStatus.BAD_REQUEST, "Failed to validate " + gvk, null, null, new Object[] {gvk, errors});
        this.errors = errors;
    }

    public ValidationResults getErrors() {
        return errors;
    }
}
