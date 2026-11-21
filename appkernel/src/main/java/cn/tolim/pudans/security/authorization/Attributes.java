package cn.tolim.pudans.security.authorization;


public interface Attributes {


    // 实际请求动作，参与规则动词匹配
    String getVerb();


    // 主资源类型，非资源请求时为空
    String getResource();


    // 子资源类型，与主资源拼合后匹配
    String getSubresource();


    // 资源实例名称，用于资源名规则匹配
    String getName();


    // API 组，核心组使用空字符串表示
    String getApiGroup();


    // 资源所属的 API 版本
    String getApiVersion();


    // 资源与非资源请求采用不同的匹配逻辑
    boolean isResourceRequest();


    // 非资源请求按应用内路径匹配
    String getPath();

    // 子资源名称，与资源名拼合后匹配
    String getSubName();
}
