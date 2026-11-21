package cn.tolim.pudans.extension.api.exception;

import cn.tolim.pudans.extension.api.GroupVersionKind;
import org.springframework.http.HttpStatus;


public class SchemeNotFoundException extends ExtensionException {

    public SchemeNotFoundException(GroupVersionKind gvk) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "未找到以下对象对应的 Scheme：" + gvk, null, null, new Object[] {gvk});
    }
}
