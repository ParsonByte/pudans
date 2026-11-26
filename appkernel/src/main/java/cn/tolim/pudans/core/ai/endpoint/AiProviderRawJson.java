package cn.tolim.pudans.core.ai.endpoint;

import cn.tolim.pudans.infra.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

final class AiProviderRawJson {

    private AiProviderRawJson() {}

    // 原样转发供应商 JSON，同时保持标准 JSON 内容类型。
    static Mono<ServerResponse> response(String body) {
        try {
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JsonMapper.shared().readTree(body));
        } catch (Exception error) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ai-provider-request-invalid-response",
                    "供应商接口返回了无法解析的数据。",
                    error));
        }
    }
}
