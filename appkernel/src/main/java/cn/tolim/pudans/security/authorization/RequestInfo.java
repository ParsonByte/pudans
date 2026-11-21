package cn.tolim.pudans.security.authorization;

import java.util.Objects;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;


@Getter
@ToString
public class RequestInfo {
    final String path;
    boolean isResourceRequest;
    String namespace;
    String userspace;
    String verb;
    String apiPrefix;
    String apiGroup;
    String apiVersion;
    String resource;

    String name;

    String subresource;

    String subName;

    String[] parts;

    public RequestInfo(boolean isResourceRequest, String path, String verb) {
        this(isResourceRequest, path, null, null, verb, null, null, null, null, null, null, null, null);
    }

    public RequestInfo(
            boolean isResourceRequest,
            String path,
            String namespace,
            String userspace,
            String verb,
            String apiPrefix,
            String apiGroup,
            String apiVersion,
            String resource,
            String name,
            String subresource,
            String subName,
            String[] parts) {
        this.isResourceRequest = isResourceRequest;
        this.path = StringUtils.defaultString(path);
        this.namespace = StringUtils.defaultString(namespace);
        this.userspace = StringUtils.defaultString(userspace);
        this.verb = StringUtils.defaultString(verb);
        this.apiPrefix = StringUtils.defaultString(apiPrefix);
        this.apiGroup = StringUtils.defaultString(apiGroup);
        this.apiVersion = StringUtils.defaultString(apiVersion);
        this.resource = StringUtils.defaultString(resource);
        this.subresource = StringUtils.defaultString(subresource);
        this.subName = StringUtils.defaultString(subName);
        this.name = StringUtils.defaultString(name);
        this.parts = Objects.requireNonNullElseGet(parts, () -> new String[] {});
    }
}
