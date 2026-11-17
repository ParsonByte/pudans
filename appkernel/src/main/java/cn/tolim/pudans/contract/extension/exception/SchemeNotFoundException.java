package cn.tolim.pudans.contract.extension.exception;

import cn.tolim.pudans.contract.extension.GroupVersionKind;
import org.springframework.http.HttpStatus;


public class SchemeNotFoundException extends ExtensionException {

    public SchemeNotFoundException(GroupVersionKind gvk) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "未找到以下对象对应的 Scheme：" + gvk, null, null, new Object[] {gvk});
    }
}
