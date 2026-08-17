package cn.tolim.pudans.contract.extension;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


public record GroupVersionKind(String group, String version, String kind) {

    public GroupVersionKind {
        Assert.hasText(version, "Version must not be blank");
        Assert.hasText(kind, "Kind must not be blank");
    }

    public static GroupVersionKind fromAPIVersionAndKind(String apiVersion, String kind) {
        Assert.hasText(kind, "Kind must not be blank");

        var gv = GroupVersion.parseAPIVersion(apiVersion);
        return new GroupVersionKind(gv.group(), gv.version(), kind);
    }

    public static <T extends Extension> GroupVersionKind fromExtension(Class<T> extension) {
        GVK gvk = extension.getAnnotation(GVK.class);
        return new GroupVersionKind(gvk.group(), gvk.version(), gvk.kind());
    }

    public GroupVersion groupVersion() {
        return new GroupVersion(group, version);
    }

    public GroupKind groupKind() {
        return new GroupKind(group, kind);
    }

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
