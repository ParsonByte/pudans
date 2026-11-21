package cn.tolim.pudans.extension.contract;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


public record GroupVersionKind(String group, String version, String kind) {

    // 校验版本与 Kind 非空，确保标识可解析。
    public GroupVersionKind {
        Assert.hasText(version, "版本不能为空");
        Assert.hasText(kind, "Kind 不能为空");
    }

    // 组合 apiVersion 与 Kind 得到完整资源标识。
    public static GroupVersionKind fromAPIVersionAndKind(String apiVersion, String kind) {
        Assert.hasText(kind, "Kind 不能为空");

        var gv = GroupVersion.parseAPIVersion(apiVersion);
        return new GroupVersionKind(gv.group(), gv.version(), kind);
    }

    // 读取扩展类上的 GVK 注解构造标识。
    public static <T extends Extension> GroupVersionKind fromExtension(Class<T> extension) {
        GVK gvk = extension.getAnnotation(GVK.class);
        return new GroupVersionKind(gvk.group(), gvk.version(), gvk.kind());
    }

    // 提取 group 与 version 组成的版本标识。
    public GroupVersion groupVersion() {
        return new GroupVersion(group, version);
    }

    // 提取 group 与 kind 组成的资源类型标识。
    public GroupKind groupKind() {
        return new GroupKind(group, kind);
    }

    // 判断是否属于带 group 的自定义资源组。
    public boolean hasGroup() {
        return StringUtils.hasText(group);
    }

    @Override
    public String toString() {
        if (hasGroup()) {
            return group + "/" + version + "/" + kind;
        }
        return version + "/" + kind;
    }
}
