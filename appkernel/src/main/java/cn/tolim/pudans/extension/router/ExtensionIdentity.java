package cn.tolim.pudans.extension.router;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.JsonExtension;
import cn.tolim.pudans.extension.contract.Scheme;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;


final class ExtensionIdentity {

    private ExtensionIdentity() {}

    // 更新时禁止修改 apiVersion、kind 与名称。
    static void requireUnchanged(Scheme scheme, String name, Extension extension) {
        if (!matches(scheme, name, extension)) {
            throw new ServerWebInputException(
                    "无法更改以下对象的 apiVersion、kind 或 metadata.name：" + scheme.groupVersionKind());
        }
    }

    // 新建时要求 apiVersion 与 kind 与方案一致。
    static void requireGroupVersionKind(Scheme scheme, Extension extension) {
        if (!matchesGroupVersionKind(scheme, extension)) {
            throw new ServerWebInputException("apiVersion 和 kind 必须与以下对象一致：" + scheme.groupVersionKind());
        }
    }

    // 校验资源标识与名称是否与路径一致。
    static boolean matches(Scheme scheme, String name, Extension extension) {
        if (!matchesGroupVersionKind(scheme, extension)) {
            return false;
        }
        try {

            if (extension instanceof JsonExtension json
                    && !json.getInternal().path("metadata").path("name").isTextual()) {
                return false;
            }
            var metadata = extension.getMetadata();
            return metadata != null
                    && StringUtils.hasText(metadata.getName())
                    && Objects.equals(name, metadata.getName());
        } catch (RuntimeException e) {

            return false;
        }
    }

    // 容错比较扩展的 apiVersion 与 kind。
    static boolean matchesGroupVersionKind(Scheme scheme, Extension extension) {
        if (extension == null) {
            return false;
        }
        try {

            if (extension instanceof JsonExtension json) {
                var node = json.getInternal();
                if (!node.path("apiVersion").isTextual() || !node.path("kind").isTextual()) {
                    return false;
                }
            }
            return Objects.equals(scheme.groupVersionKind().kind(), extension.getKind())
                    && Objects.equals(scheme.groupVersionKind().groupVersion().toString(), extension.getApiVersion());
        } catch (RuntimeException e) {

            return false;
        }
    }
}
