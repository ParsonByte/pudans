package cn.tolim.pudans.extension.api.endpoint;

import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.extension.api.GroupVersion;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;


public interface CustomEndpoint {

    RouterFunction<ServerResponse> endpoint();

    default GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
