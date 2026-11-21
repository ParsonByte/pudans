package cn.tolim.pudans.extension.contract;

import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import org.springframework.util.Assert;

public class WatcherExtensionMatchers {
    @Getter
    private final ExtensionClient client;

    private final GroupVersionKind gvk;
    private final ExtensionMatcher onAddMatcher;
    private final ExtensionMatcher onUpdateMatcher;
    private final ExtensionMatcher onDeleteMatcher;


    // 校验必需依赖，未提供匹配器时按 GVK 兜底。
    @Builder(builderMethodName = "internalBuilder")
    public WatcherExtensionMatchers(
            ExtensionClient client,
            GroupVersionKind gvk,
            ExtensionMatcher onAddMatcher,
            ExtensionMatcher onUpdateMatcher,
            ExtensionMatcher onDeleteMatcher) {
        Assert.notNull(client, "客户端不能为 null。");
        Assert.notNull(gvk, "gvk 不能为 null。");
        this.client = client;
        this.gvk = gvk;
        this.onAddMatcher = Objects.requireNonNullElseGet(onAddMatcher, () -> emptyMatcher(client, gvk));
        this.onUpdateMatcher = Objects.requireNonNullElseGet(onUpdateMatcher, () -> emptyMatcher(client, gvk));
        this.onDeleteMatcher = Objects.requireNonNullElseGet(onDeleteMatcher, () -> emptyMatcher(client, gvk));
    }

    // 以客户端与 GVK 为必需条件创建构建器。
    public static WatcherExtensionMatchersBuilder builder(ExtensionClient client, GroupVersionKind gvk) {
        return internalBuilder().gvk(gvk).client(client);
    }

    // 默认匹配器仅校验 GVK。
    static ExtensionMatcher emptyMatcher(ExtensionClient client, GroupVersionKind gvk) {
        return DefaultExtensionMatcher.builder(client, gvk).build();
    }

    // 返回监听器绑定的 GVK。
    public GroupVersionKind getGroupVersionKind() {
        return this.gvk;
    }

    // 返回包装后的新增匹配器。
    public ExtensionMatcher onAddMatcher() {
        return delegateExtensionMatcher(this.onAddMatcher);
    }

    // 返回包装后的更新匹配器。
    public ExtensionMatcher onUpdateMatcher() {
        return delegateExtensionMatcher(this.onUpdateMatcher);
    }

    // 返回包装后的删除匹配器。
    public ExtensionMatcher onDeleteMatcher() {
        return delegateExtensionMatcher(this.onDeleteMatcher);
    }

    // 先校验 GVK，再委托具体匹配器判断。
    ExtensionMatcher delegateExtensionMatcher(ExtensionMatcher matcher) {
        return extension -> extension.groupVersionKind().equals(gvk) && matcher.match(extension);
    }
}
