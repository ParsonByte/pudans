package cn.tolim.pudans.extension.gc;

import cn.tolim.pudans.extension.contract.GroupVersionKind;
import org.springframework.util.Assert;

record GcRequest(GroupVersionKind gvk, String name) {

    // 校验回收请求的必填标识参数。
    public GcRequest {
        Assert.notNull(gvk, "Group、version 和 kind 不能为 null");
        Assert.hasText(name, "扩展名称不能为空");
    }
}
