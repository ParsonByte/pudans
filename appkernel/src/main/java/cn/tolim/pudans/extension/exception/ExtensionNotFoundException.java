package cn.tolim.pudans.extension.exception;

import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.exception.ExtensionException;
import java.net.URI;
import org.springframework.http.HttpStatus;

public class ExtensionNotFoundException extends ExtensionException {

    public static final URI TYPE = URI.create("https://www.pudans.tolim.cn/api/errors/extension-not-found");

    public ExtensionNotFoundException(GroupVersionKind gvk, String name) {
        super(HttpStatus.NOT_FOUND, "Extension " + gvk + "/" + name + " was not found.", null, null, new Object[] {
                gvk, name
        });
        setType(TYPE);
    }
}
