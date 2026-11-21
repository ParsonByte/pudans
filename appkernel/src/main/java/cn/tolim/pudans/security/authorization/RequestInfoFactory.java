package cn.tolim.pudans.security.authorization;

import cn.tolim.pudans.extension.contract.ApiGroups;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;


public class RequestInfoFactory {
    public static final RequestInfoFactory INSTANCE = new RequestInfoFactory(Set.of("api", "apis"), Set.of("api"));


    final Set<String> apiPrefixes;


    final Set<String> grouplessApiPrefixes;


    final Set<String> specialVerbs;

    // 默认补充代理与监听两个特殊动作
    public RequestInfoFactory(Set<String> apiPrefixes, Set<String> grouplessApiPrefixes) {
        this(apiPrefixes, grouplessApiPrefixes, Set.of("proxy", "watch"));
    }

    // 保存 API 前缀与特殊动作配置
    public RequestInfoFactory(Set<String> apiPrefixes, Set<String> grouplessApiPrefixes, Set<String> specialVerbs) {
        this.apiPrefixes = apiPrefixes;
        this.grouplessApiPrefixes = grouplessApiPrefixes;
        this.specialVerbs = specialVerbs;
    }


    // 把请求路径解析为规则匹配所需信息
    public RequestInfo newRequestInfo(ServerHttpRequest request) {

        PathContainer path = request.getPath().pathWithinApplication();
        RequestInfo requestInfo =
                new RequestInfo(false, path.value(), request.getMethod().name().toLowerCase());

        String[] currentParts = splitPath(path.value());

        if (currentParts.length < 3) {

            return requestInfo;
        }

        if (!apiPrefixes.contains(currentParts[0])) {

            return requestInfo;
        }
        requestInfo.apiPrefix = currentParts[0];
        currentParts = Arrays.copyOfRange(currentParts, 1, currentParts.length);

        if (!grouplessApiPrefixes.contains(requestInfo.apiPrefix)) {


            if (currentParts.length < 3) {

                return requestInfo;
            }

            requestInfo.apiGroup = StringUtils.defaultString(currentParts[0]);
            currentParts = Arrays.copyOfRange(currentParts, 1, currentParts.length);
            if (!ApiGroups.API.equals(requestInfo.apiGroup)) {
                requestInfo.isResourceRequest = false;
                return requestInfo;
            }
        }
        requestInfo.isResourceRequest = true;
        requestInfo.apiVersion = currentParts[0];
        currentParts = Arrays.copyOfRange(currentParts, 1, currentParts.length);

        if (specialVerbs.contains(currentParts[0])) {
            if (currentParts.length < 2) {
                throw new IllegalArgumentException(
                        String.format("无法从 URL %s 确定 kind 和 namespace", request.getPath()));
            }
            requestInfo.verb = currentParts[0];
            currentParts = Arrays.copyOfRange(currentParts, 1, currentParts.length);
        } else {
            requestInfo.verb = switch (request.getMethod().name().toUpperCase()) {
                case "POST" -> "create";
                case "GET", "HEAD" -> "get";
                case "PUT" -> "update";
                case "PATCH" -> "patch";
                case "DELETE" -> "delete";
                default -> "";
            };
        }


        Set<String> namespaceSubresources = Set.of("status", "finalize");
        if (Objects.equals(currentParts[0], "namespaces")) {
            if (currentParts.length > 1) {
                requestInfo.namespace = currentParts[1];


                if (currentParts.length > 2 && !namespaceSubresources.contains(currentParts[2])) {
                    currentParts = Arrays.copyOfRange(currentParts, 2, currentParts.length);
                }
            }
        } else if ("userspaces".equals(currentParts[0])) {
            if (currentParts.length > 1) {
                requestInfo.userspace = currentParts[1];


                if (currentParts.length > 2) {
                    currentParts = Arrays.copyOfRange(currentParts, 2, currentParts.length);
                }
            }
        } else {
            requestInfo.userspace = "";
            requestInfo.namespace = "";
        }


        requestInfo.parts = currentParts;


        if (requestInfo.parts.length >= 3
                && "auditlogs".equals(requestInfo.parts[0])
                && "replay".equals(requestInfo.parts[1])) {
            requestInfo.parts = new String[] {requestInfo.parts[0], requestInfo.parts[2], "replay"};
        }


        if (requestInfo.parts.length >= 3 && !specialVerbs.contains(requestInfo.verb)) {
            requestInfo.subresource = requestInfo.parts[2];

            if (requestInfo.parts.length >= 4) {
                requestInfo.subName = requestInfo.parts[3];
            }
        }

        if (requestInfo.parts.length >= 2) {
            requestInfo.name = requestInfo.parts[1];
        }

        if (requestInfo.parts.length >= 1) {
            requestInfo.resource = requestInfo.parts[0];
        }


        if (StringUtils.isNotBlank(requestInfo.name)
                && StringUtils.isBlank(requestInfo.subresource)
                && "create".equals(requestInfo.verb)) {
            requestInfo.isResourceRequest = false;
        }


        if (requestInfo.name.isEmpty() && "get".equals(requestInfo.verb)) {
            var watch = request.getQueryParams().getFirst("watch");
            if (Boolean.parseBoolean(watch)) {
                requestInfo.verb = "watch";
            } else {
                requestInfo.verb = "list";
            }
        }


        if (Objects.equals(requestInfo.verb, "delete")) {
            var deleteAll = request.getQueryParams().getFirst("all");
            if (Boolean.parseBoolean(deleteAll)) {
                requestInfo.verb = "deletecollection";
            }
        }
        return requestInfo;
    }

    // 去除首尾斜杠后按路径段拆分
    private String[] splitPath(String path) {
        path = StringUtils.strip(path, "/");
        if (StringUtils.isEmpty(path)) {
            return new String[] {};
        }
        return StringUtils.split(path, "/");
    }
}
