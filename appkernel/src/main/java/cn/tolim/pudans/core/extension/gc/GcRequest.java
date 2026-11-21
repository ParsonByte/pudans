package cn.tolim.pudans.core.extension.gc;

import cn.tolim.pudans.contract.extension.GroupVersionKind;
import org.springframework.util.Assert;

record GcRequest(GroupVersionKind gvk, String name) {

    public GcRequest {
        Assert.notNull(gvk, "Group、version 和 kind 不能为 null");
        Assert.hasText(name, "扩展名称不能为空");
    }
}
