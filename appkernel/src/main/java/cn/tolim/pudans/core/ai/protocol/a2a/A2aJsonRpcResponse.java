package cn.tolim.pudans.core.ai.protocol.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

// id 使用 JSON 标量而非 JsonNode，避免 Jackson 2/3 混用时序列化成空对象。
public sealed interface A2aJsonRpcResponse {

    String VERSION = "2.0";

    static A2aJsonRpcResponse result(Object id, Object result) {
        return new Result(VERSION, id, result);
    }

    static A2aJsonRpcResponse error(@Nullable Object id, int code, String message) {
        return new Error(VERSION, id, new Detail(code, message));
    }

    record Result(String jsonrpc, Object id, Object result) implements A2aJsonRpcResponse {}

    record Error(String jsonrpc, @Nullable Object id, Detail error) implements A2aJsonRpcResponse {

        // JSON-RPC 解析失败时也必须输出 null id，全局 NON_NULL 策略下需显式覆盖。
        @Override
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public @Nullable Object id() {
            return id;
        }
    }

    record Detail(int code, String message) {}
}