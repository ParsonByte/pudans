package cn.tolim.pudans.core.ai.protocol.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class A2aJsonRpcRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesAValidRequest() {
        var request = A2aJsonRpcRequest.parse("""
                {"jsonrpc":"2.0","id":"req-1","method":"message/send",
                 "params":{"message":{"role":"user","messageId":"m-1"}}}
                """, mapper);

        assertThat(request.id()).isEqualTo("req-1");
        assertThat(request.method()).isEqualTo("message/send");
        assertThat(request.params().path("message").path("messageId").asText()).isEqualTo("m-1");
    }

    @Test
    void rejectsAnEmptyBody() {
        var error = Objects.requireNonNull(catchThrowableOfType(
                () -> A2aJsonRpcRequest.parse("", mapper), A2aRpcException.class));

        assertThat(error.code()).isEqualTo(A2aRpcException.PARSE_ERROR);
        assertThat(error.id()).isNull();
    }

    @Test
    void rejectsMalformedJson() {
        var error = Objects.requireNonNull(catchThrowableOfType(
                () -> A2aJsonRpcRequest.parse("{", mapper), A2aRpcException.class));

        assertThat(error.code()).isEqualTo(A2aRpcException.PARSE_ERROR);
    }

    @Test
    void rejectsANonObjectRequest() {
        var error = Objects.requireNonNull(catchThrowableOfType(
                () -> A2aJsonRpcRequest.parse("[]", mapper), A2aRpcException.class));

        assertThat(error.code()).isEqualTo(A2aRpcException.INVALID_REQUEST);
    }

    @Test
    void rejectsAMissingId() {
        var error = Objects.requireNonNull(catchThrowableOfType(
                () -> A2aJsonRpcRequest.parse("""
                        {"jsonrpc":"2.0","method":"tasks/get","params":{"id":"task-1"}}
                        """, mapper),
                A2aRpcException.class));

        assertThat(error.code()).isEqualTo(A2aRpcException.INVALID_REQUEST);
    }

    @Test
    void rejectsAMissingVersion() {
        var error = Objects.requireNonNull(catchThrowableOfType(
                () -> A2aJsonRpcRequest.parse("""
                        {"id":1,"method":"tasks/get","params":{"id":"task-1"}}
                        """, mapper),
                A2aRpcException.class));

        assertThat(error.code()).isEqualTo(A2aRpcException.INVALID_REQUEST);
        assertThat(error.id()).isEqualTo(1L);
    }

    @Test
    void rejectsAMissingMethod() {
        var error = Objects.requireNonNull(catchThrowableOfType(
                () -> A2aJsonRpcRequest.parse("""
                        {"jsonrpc":"2.0","id":1,"params":{}}
                        """, mapper),
                A2aRpcException.class));

        assertThat(error.code()).isEqualTo(A2aRpcException.INVALID_REQUEST);
        assertThat(error.id()).isEqualTo(1L);
    }
}