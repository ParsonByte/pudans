package cn.tolim.pudans.core.ai.protocol.agui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AguiRunRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesStringContentAndForwardedAgent() {
        var request = AguiRunRequest.parse(
                MAPPER,
                """
                {"threadId":"thread-1","runId":"run-1","messages":[
                  {"id":"m1","role":"user","content":"第一轮"},
                  {"id":"m2","role":"assistant","content":"旧回答"},
                  {"id":"m3","role":"user","content":"你好"}],
                 "tools":[],"context":[],"state":{},"forwardedProps":{"agent":"agent-a"}}
                """,
                null);

        assertThat(request.threadId()).isEqualTo("thread-1");
        assertThat(request.runId()).isEqualTo("run-1");
        assertThat(request.agentName()).isEqualTo("agent-a");
        assertThat(request.input()).isEqualTo("你好");
    }

    @Test
    void parsesTextPartsAndFallsBackToHeaderAgent() {
        var request = AguiRunRequest.parse(
                MAPPER,
                """
                {"messages":[{"id":"m1","role":"user","content":[
                  {"type":"text","text":"你"},{"type":"text","text":"好"}]}],
                 "forwardedProps":{"agent":" "}}
                """,
                "agent-b");

        assertThat(request.threadId()).startsWith("thread-");
        assertThat(request.runId()).startsWith("run-");
        assertThat(request.agentName()).isEqualTo("agent-b");
        assertThat(request.input()).isEqualTo("你好");
    }

    @Test
    void ignoresNonUserMessagesWhenSelectingTheInput() {
        var request = AguiRunRequest.parse(
                MAPPER,
                """
                {"threadId":"thread-1","messages":[
                  {"id":"m1","role":"user","content":"第一轮"},
                  {"id":"m2","role":"tool","content":"工具结果"},
                  {"id":"m3","role":"assistant","content":"回答"}]}
                """,
                null);

        assertThat(request.input()).isEqualTo("第一轮");
        assertThat(request.agentName()).isNull();
    }

    @Test
    void rejectsARequestWithoutUserMessages() {
        assertThatThrownBy(() -> AguiRunRequest.parse(
                        MAPPER,
                        """
                        {"threadId":"thread-1","messages":[{"id":"m1","role":"assistant","content":"回答"}]}
                        """,
                        null))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo("ai-agui-messages-invalid");
                });
    }

    @Test
    void rejectsAnInvalidBody() {
        assertThatThrownBy(() -> AguiRunRequest.parse(MAPPER, "not-json", null))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo("ai-agui-body-invalid");
                });
    }

    @Test
    void rejectsABlankBody() {
        assertThatThrownBy(() -> AguiRunRequest.parse(MAPPER, " ", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("请求体不能为空");
    }
}