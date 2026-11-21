package cn.tolim.pudans.extension.contract.exception;

import cn.tolim.pudans.extension.contract.GroupVersionKind;
import org.springframework.http.HttpStatus;


public class SchemeNotFoundException extends ExtensionException {

    // 以 GVK 构造内部错误，提示缺少 Scheme。
    public SchemeNotFoundException(GroupVersionKind gvk) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "未找到以下对象对应的 Scheme：" + gvk, null, null, new Object[] {gvk});
    }
}
