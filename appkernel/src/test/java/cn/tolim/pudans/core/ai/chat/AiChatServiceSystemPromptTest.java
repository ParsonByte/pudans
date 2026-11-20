package cn.tolim.pudans.core.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiChatModeService;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
import cn.tolim.pudans.core.ai.embedding.AiEmbeddingService;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkitFactory;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.memory.AiMemoryContextBuilder;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseAcl;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalService;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import cn.tolim.pudans.core.storage.StorageService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.PudansTime;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiChatServiceSystemPromptTest {

    @Mock
    AiSettingService settingService;

    @Mock
    AiChatModeService chatModeService;

    @Mock
    AiTeamService teamService;

    @Mock
    AiConversationService conversationService;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    KnowledgeBaseAcl knowledgeBaseAcl;

    @Mock
    KnowledgeBaseRetrievalService retrievalService;

    @Mock
    AiEmbeddingService embeddingService;

    @Mock
    AiMemoryContextBuilder memoryContextBuilder;

    @Mock
    AiGatewayService gatewayService;

    @Mock
    MemoryTaskScheduler memoryTaskScheduler;

    @Mock
    StorageService storageService;

    @Mock
    AiAgentContextService agentContextService;

    @Mock
    AiChatToolService toolService;

    @Mock
    cn.tolim.pudans.core.ai.library.command.SlashCommandService slashCommandService;

    @Mock
    cn.tolim.pudans.core.ai.library.hook.HookService hookService;

    @Mock
    cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore journalStore;

    @Mock
    cn.tolim.pudans.core.ai.harness.agent.AgentStateStore stateStore;

    @Mock
    SubagentToolFactory subagentToolFactory;

    @Mock
    HarnessToolkitFactory harnessToolkitFactory;

    @Mock
    cn.tolim.pudans.core.ai.library.mcp.McpServerService mcpServerService;

    @Mock
    cn.tolim.pudans.core.ai.harness.observability.HarnessObservability harnessObservability;

    private AiChatService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(journalStore.open(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new cn.tolim.pudans.core.ai.harness.journal.InMemoryThreadJournal()));
        service = new AiChatService(
                settingService,
                chatModeService,
                teamService,
                conversationService,
                client,
                knowledgeBaseAcl,
                retrievalService,
                embeddingService,
                memoryContextBuilder,
                gatewayService,
                memoryTaskScheduler,
                storageService,
                agentContextService,
                toolService,
                slashCommandService,
                hookService,
                journalStore,
                stateStore,
                subagentToolFactory,
                harnessToolkitFactory,
                mcpServerService,
                harnessObservability);
    }

    @Test
    void usesDefaultPromptWhenTheAgentHasNoPrompt() {
        var system = service.systemContent("", MemoryInjection.empty());

        assertThat(system)
                .contains("PUDANS AI 助手")
                .contains(LocalDate.now(PudansTime.BEIJING).toString())
                .contains("ChatGPT");
    }

    @Test
    void keepsACustomAgentPrompt() {
        var system = service.systemContent("自定义提示", MemoryInjection.empty());

        assertThat(system).isEqualTo("自定义提示");
    }

    @Test
    void loadsAnOwnedImageAttachment() {
        var file = imageFile("user-1");
        when(storageService.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageService.readBytes("file-1", 25 * 1024 * 1024))
                .thenReturn(Mono.just(new byte[] {1, 2, 3}));

        StepVerifier.create(service.attachmentPart(
                        "user-1", new AiChatCompletionRequest.AiChatAttachment("file-1", "IMAGE")))
                .assertNext(part -> {
                    assertThat(part).isInstanceOf(AiContentPart.Image.class);
                    var image = (AiContentPart.Image) part;
                    assertThat(image.mediaType()).isEqualTo("image/png");
                    assertThat(image.data()).isEqualTo("AQID");
                })
                .verifyComplete();
    }

    @Test
    void loadsADocumentAttachmentAsAFilePart() {
        var file = attachmentFile("user-1", "notes.pdf", "application/pdf");
        when(storageService.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageService.readBytes("file-1", 25 * 1024 * 1024))
                .thenReturn(Mono.just(new byte[] {4, 5, 6}));

        StepVerifier.create(service.attachmentPart(
                        "user-1", new AiChatCompletionRequest.AiChatAttachment("file-1", "DOCUMENT")))
                .assertNext(part -> {
                    assertThat(part).isInstanceOf(AiContentPart.File.class);
                    var document = (AiContentPart.File) part;
                    assertThat(document.mediaType()).isEqualTo("application/pdf");
                    assertThat(document.filename()).isEqualTo("notes.pdf");
                    assertThat(document.data()).isEqualTo("BAUG");
                })
                .verifyComplete();
    }

    @Test
    void loadsATextDocumentAsTextContent() {
        var file = attachmentFile("user-1", "notes.md", "text/markdown");
        when(storageService.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageService.readBytes("file-1", 25 * 1024 * 1024))
                .thenReturn(Mono.just("# 标题\n正文".getBytes(StandardCharsets.UTF_8)));

        StepVerifier.create(service.attachmentPart(
                        "user-1", new AiChatCompletionRequest.AiChatAttachment("file-1", "DOCUMENT")))
                .assertNext(part -> {
                    assertThat(part).isInstanceOf(AiContentPart.Text.class);
                    var text = (AiContentPart.Text) part;
                    assertThat(text.text()).contains("notes.md").contains("# 标题").contains("正文");
                })
                .verifyComplete();
    }

    @Test
    void rejectsAnAttachmentOwnedByAnotherUser() {
        when(storageService.getFile("file-1")).thenReturn(Mono.just(imageFile("user-2")));

        StepVerifier.create(service.attachmentPart(
                        "user-1", new AiChatCompletionRequest.AiChatAttachment("file-1", "IMAGE")))
                .expectError(ApiException.class)
                .verify();
    }

    private static StorageFile imageFile(String owner) {
        return attachmentFile(owner, "image.png", "image/png");
    }

    private static StorageFile attachmentFile(String owner, String name, String contentType) {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("file-1");
        file.setMetadata(metadata);
        file.getSpec().setOwner(owner);
        file.getSpec().setState(StorageFileState.ACTIVE);
        file.getSpec().setContentType(contentType);
        file.getSpec().setOriginalName(name);
        file.getSpec().setSize(3);
        return file;
    }
}
