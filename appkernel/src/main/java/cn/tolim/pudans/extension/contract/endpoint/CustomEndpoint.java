package cn.tolim.pudans.extension.contract.endpoint;

import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;


public interface CustomEndpoint {

    // 返回该扩展的自定义路由函数。
    RouterFunction<ServerResponse> endpoint();

    // 默认使用平台 API 版本作为路由分组。
    default GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
