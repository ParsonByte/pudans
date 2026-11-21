package cn.tolim.pudans.security.authorization;


public class AttributesRecord implements Attributes {
    private final RequestInfo requestInfo;

    // 把请求解析结果适配为规则匹配属性
    public AttributesRecord(RequestInfo requestInfo) {
        this.requestInfo = requestInfo;
    }

    // 规则动词匹配使用请求的实际动作
    @Override
    public String getVerb() {
        return requestInfo.getVerb();
    }

    // 规则资源匹配使用请求的主资源
    @Override
    public String getResource() {
        return requestInfo.getResource();
    }

    // 子资源需与主资源拼合参与匹配
    @Override
    public String getSubresource() {
        return requestInfo.getSubresource();
    }

    // 资源实例名用于资源名模式匹配
    @Override
    public String getName() {
        return requestInfo.getName();
    }

    // API 组参与规则分组匹配
    @Override
    public String getApiGroup() {
        return requestInfo.getApiGroup();
    }

    // API 版本随请求信息一并暴露
    @Override
    public String getApiVersion() {
        return requestInfo.getApiVersion();
    }

    // 决定采用资源还是非资源匹配逻辑
    @Override
    public boolean isResourceRequest() {
        return requestInfo.isResourceRequest();
    }

    // 非资源请求使用路径进行规则匹配
    @Override
    public String getPath() {
        return requestInfo.getPath();
    }

    // 子资源名与资源名拼合后匹配
    @Override
    public String getSubName() {
        return requestInfo.getSubName();
    }
}
