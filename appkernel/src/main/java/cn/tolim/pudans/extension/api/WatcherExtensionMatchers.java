package cn.tolim.pudans.extension.api;

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

    public static WatcherExtensionMatchersBuilder builder(ExtensionClient client, GroupVersionKind gvk) {
        return internalBuilder().gvk(gvk).client(client);
    }

    static ExtensionMatcher emptyMatcher(ExtensionClient client, GroupVersionKind gvk) {
        return DefaultExtensionMatcher.builder(client, gvk).build();
    }

    public GroupVersionKind getGroupVersionKind() {
        return this.gvk;
    }

    public ExtensionMatcher onAddMatcher() {
        return delegateExtensionMatcher(this.onAddMatcher);
    }

    public ExtensionMatcher onUpdateMatcher() {
        return delegateExtensionMatcher(this.onUpdateMatcher);
    }

    public ExtensionMatcher onDeleteMatcher() {
        return delegateExtensionMatcher(this.onDeleteMatcher);
    }

    ExtensionMatcher delegateExtensionMatcher(ExtensionMatcher matcher) {
        return extension -> extension.groupVersionKind().equals(gvk) && matcher.match(extension);
    }
}
