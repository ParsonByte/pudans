package cn.tolim.pudans.extension.gc;

import cn.tolim.pudans.contract.extension.GroupVersionKind;
import org.springframework.util.Assert;

record GcRequest(GroupVersionKind gvk, String name) {

    public GcRequest {
        Assert.notNull(gvk, "Group, version and kind must not be null");
        Assert.hasText(name, "Extension name must not be blank");
    }
}
