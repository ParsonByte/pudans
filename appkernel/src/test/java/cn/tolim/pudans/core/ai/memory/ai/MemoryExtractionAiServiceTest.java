package cn.tolim.pudans.core.ai.memory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.generation.AiTextGenerationService;
import cn.tolim.pudans.core.ai.generation.GenerationRequest;
import cn.tolim.pudans.core.ai.generation.GenerationResult;
import cn.tolim.pudans.core.ai.memory.ExtractedMemory;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryExtractionAiServiceTest {

    private static final MemoryContext CONTEXT =
            new MemoryContext("team-1", "user-1", "agent-1", "session-1", null);

    private static final Instant CREATED_AT = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    AiTextGenerationService generationService;

    @Mock
    AiSettingService settingService;

    private MemoryExtractionAiService service;

    @BeforeEach
    void setUp() {
        service = new MemoryExtractionAiService(
                new MemoryAiSupport(generationService, settingService, new ObjectMapper()));
    }

    @Test
    void mapsScenesAndMemoriesFromJson() {
        stubGeneration("""
                {"scenes":[{"sceneName":"发布流程","messageIds":[101,102,999],
                  "memories":[
                    {"content":"每周五发布","type":"EPISODIC","priority":60,"sceneName":"发布流程",
                     "metadata":{"source":"chat"}},
                    {"content":"禁止周五发布","type":"INSTRUCTION","priority":-1,"sceneName":"发布流程"},
                    {"content":"","type":"EPISODIC","sceneName":"发布流程"},
                    {"content":"无来源","type":"EPISODIC","sceneName":"发布流程","sourceMessageIds":[999]}]}]}
                """);

        var outcome = service.extract(CONTEXT, 9L, List.of(
                        message(101L, "user", "我们每周五发布。"),
                        message(102L, "assistant", "已记录。")))
                .block();

        assertThat(outcome).isNotNull();
        assertThat(outcome.modelCode()).isEqualTo("model-code");
        assertThat(outcome.scenes()).hasSize(1);
        var scene = outcome.scenes().getFirst();
        assertThat(scene.sceneName()).isEqualTo("发布流程");
        assertThat(scene.messageIds()).containsExactly("101", "102");
        assertThat(scene.memories())
                .extracting(ExtractedMemory::recordId)
                .containsExactly("mem-101-0", "mem-101-1");
        assertThat(scene.memories().getFirst().type()).isEqualTo(MemoryType.EPISODIC);
        assertThat(scene.memories().getFirst().priority()).isEqualTo(60);
        assertThat(scene.memories().getFirst().sourceMessageIds()).containsExactly("101", "102");
        assertThat(scene.memories().getFirst().metadata()).containsEntry("source", "chat");
        assertThat(scene.memories().get(1).type()).isEqualTo(MemoryType.INSTRUCTION);
        assertThat(scene.memories().get(1).priority()).isEqualTo(-1);
    }

    @Test
    void usesTheConfiguredRouteGroupAndStripsJsonFences() {
        var spec = new AiSetting.Spec();
        spec.getMemory().setExtractionRouteGroup("memory-group");
        when(settingService.spec()).thenReturn(Mono.just(spec));
        when(generationService.generate(any())).thenReturn(Mono.just(new GenerationResult(
                """
                        ```json
                        {"scenes":[{"sceneName":"排障","messageIds":[1],
                          "memories":[{"content":"检查日志","type":"WORK_METHOD","priority":40,
                          "sceneName":"排障"}]}]}
                        ```
                        """,
                "model-code",
                "provider-a",
                "account-a")));

        var outcome = service.extract(CONTEXT, 1L, List.of(message(1L, "user", "先检查日志")))
                .block();

        assertThat(outcome).isNotNull();
        assertThat(outcome.scenes()).hasSize(1);
        assertThat(outcome.scenes().getFirst().memories().getFirst().type())
                .isEqualTo(MemoryType.WORK_METHOD);

        var captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationService).generate(captor.capture());
        var request = captor.getValue();
        assertThat(request.routeGroupCode()).isEqualTo("memory-group");
        assertThat(request.temperature()).isEqualTo(0.1);
        assertThat(request.maxOutputTokens()).isEqualTo(2048);
        assertThat(request.systemPrompt()).contains("JSON 结构");
        assertThat(request.userPrompt()).contains("[1] user: 先检查日志");
    }

    @Test
    void rejectsMalformedJson() {
        stubGeneration("not-json");

        StepVerifier.create(service.extract(CONTEXT, 1L, List.of(message(1L, "user", "hi"))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode().value()).isEqualTo(502);
                    assertThat(apiException.getCode()).isEqualTo("ai-memory-generation-invalid");
                })
                .verify();
    }

    @Test
    void returnsEmptyOutcomeWithoutCallingTheModelWhenNoMessagesExist() {
        var outcome = service.extract(CONTEXT, 1L, List.of()).block();

        assertThat(outcome).isNotNull();
        assertThat(outcome.scenes()).isEmpty();
        assertThat(outcome.modelCode()).isNull();
        verifyNoInteractions(generationService, settingService);
    }

    private void stubGeneration(String content) {
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(generationService.generate(any())).thenReturn(Mono.just(
                new GenerationResult(content, "model-code", "provider-a", "account-a")));
    }

    private static Message message(long id, String role, String content) {
        return new Message(
                id,
                9L,
                "space-1",
                "user-1",
                "team-1",
                "agent-1",
                "session-1",
                null,
                null,
                1,
                0,
                role,
                content,
                "text",
                null,
                null,
                CREATED_AT);
    }
}
