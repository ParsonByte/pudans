package cn.tolim.pudans.extension.router;

import cn.tolim.pudans.contract.extension.Extension;
import cn.tolim.pudans.contract.extension.JsonExtension;
import cn.tolim.pudans.contract.extension.Scheme;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;


final class ExtensionIdentity {

    private ExtensionIdentity() {}

    static void requireUnchanged(Scheme scheme, String name, Extension extension) {
        if (!matches(scheme, name, extension)) {
            throw new ServerWebInputException(
                    "无法更改以下对象的 apiVersion、kind 或 metadata.name：" + scheme.groupVersionKind());
        }
    }

    static void requireGroupVersionKind(Scheme scheme, Extension extension) {
        if (!matchesGroupVersionKind(scheme, extension)) {
            throw new ServerWebInputException("apiVersion 和 kind 必须与以下对象一致：" + scheme.groupVersionKind());
        }
    }

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
