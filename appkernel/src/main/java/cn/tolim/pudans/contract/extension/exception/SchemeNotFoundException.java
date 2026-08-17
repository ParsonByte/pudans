package cn.tolim.pudans.contract.extension.exception;

import cn.tolim.pudans.contract.extension.GroupVersionKind;
import org.springframework.http.HttpStatus;


public class SchemeNotFoundException extends ExtensionException {

    public SchemeNotFoundException(GroupVersionKind gvk) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Scheme not found for " + gvk, null, null, new Object[] {gvk});
    }
}
