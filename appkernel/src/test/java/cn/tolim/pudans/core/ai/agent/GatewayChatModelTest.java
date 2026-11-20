package cn.tolim.pudans.core.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiGatewayInvocation;
import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GatewayChatModelTest {

    private final AiGatewayService gatewayService = mock(AiGatewayService.class);

    @Test
    void mapsTheChatRequestToTheGatewayAndBack() {
        var captured = new AtomicReference<AiGatewayInvocation>();
        when(gatewayService.complete(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Mono.just(result());
        });
        var model = new GatewayChatModel(gatewayService, context());
        var tool = ToolSpecification.builder()
                .name("get_weather")
                .description("Weather lookup")
                .parameters(JsonObjectSchema.builder().addStringProperty("city").build())
                .build();
        var request = ChatRequest.builder()
                .messages(SystemMessage.from("system"), UserMessage.from("hello"))
                .modelName("gpt-x")
                .temperature(0.2)
                .maxOutputTokens(100)
                .toolSpecifications(List.of(tool))
                .toolChoice(ToolChoice.AUTO)
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response.aiMessage().text()).isEqualTo("hi");
        assertThat(response.aiMessage().thinking()).isEqualTo("think");
        assertThat(response.aiMessage().toolExecutionRequests()).singleElement()
                .isInstanceOfSatisfying(ToolExecutionRequest.class, call -> {
                    assertThat(call.id()).isEqualTo("call_1");
                    assertThat(call.name()).isEqualTo("get_weather");
                });
        assertThat(response.id()).isEqualTo("resp-1");
        assertThat(response.modelName()).isEqualTo("gpt-x");
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);

        var invocation = captured.get();
        assertThat(invocation.userId()).isEqualTo("user-1");
        assertThat(invocation.agentSource()).isEqualTo("pudans-subagent");
        assertThat(invocation.agentName()).isEqualTo("agent-a");
        assertThat(invocation.routeGroupCode()).isEqualTo("fast");
        assertThat(invocation.modelOverride()).isEqualTo("gpt-x");
        assertThat(invocation.ir().messages()).hasSize(2);
        assertThat(invocation.ir().messages().get(0).role()).isEqualTo("system");
        assertThat(invocation.ir().messages().get(1).role()).isEqualTo("user");
        assertThat(invocation.ir().requestParams().model()).isEqualTo("gpt-x");
        assertThat(invocation.ir().requestParams().temperature()).isEqualTo(0.2);
        assertThat(invocation.ir().requestParams().maxTokens()).isEqualTo(100);
        assertThat(invocation.ir().requestParams().toolChoice().asText()).isEqualTo("auto");
        assertThat(invocation.ir().tools()).singleElement()
                .satisfies(toolSpec -> assertThat(toolSpec.name()).isEqualTo("get_weather"));
    }

    private static GatewayChatContext context() {
        return new GatewayChatContext(
                "user-1",
                "pudans-subagent",
                "subagent-agent-a",
                null,
                "req-1",
                "idem-1",
                null,
                "fast",
                "agent-a",
                null,
                "gpt-x");
    }

    private static AiGatewayResult result() {
        var arguments = JsonUtils.mapper().createObjectNode().put("city", "Paris");
        var response = new AiChatResponse(
                "resp-1",
                "gpt-x",
                new AiChatMessage(
                        "assistant",
                        List.of(
                                new AiContentPart.Thinking("think", null, null),
                                new AiContentPart.Text("hi", null),
                                new AiContentPart.ToolCall(
                                        "call_1", "get_weather", arguments, null))),
                new AiTokenUsage(1, 2, null, null),
                "tool_calls");
        return new AiGatewayResult(
                response,
                new AiGatewayMeta(
                        "req-1", 1, "chat", "fast", "openai", "account-1", "gpt-x",
                        null, "team-1", "agent-a", "session-1"),
                new AiTokenUsage(1, 2, null, null),
                BigDecimal.ONE,
                "wallet");
    }
}