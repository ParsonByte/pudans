package cn.tolim.pudans.core.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.storage.resource.StorageFileState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.ChatModes;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.conversation.Conversations;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkitFactory;
import cn.tolim.pudans.core.ai.gateway.AiGateway;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.memory.AiMemoryContextBuilder;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseAcl;
import cn.tolim.pudans.core.ai.rag.KnowledgeRetrieval;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.core.storage.StorageTransfer;
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
class ChatCompletionsSystemPromptTest {

    @Mock
    AiSettings aiSettings;

    @Mock
    ChatModes chatModes;

    @Mock
    Teams teams;

    @Mock
    Conversations conversations;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    KnowledgeBaseAcl knowledgeBaseAcl;

    @Mock
    KnowledgeRetrieval knowledgeRetrieval;

    @Mock
    Embeddings embeddings;

    @Mock
    AiMemoryContextBuilder memoryContextBuilder;

    @Mock
    AiGateway aiGateway;

    @Mock
    MemoryTaskScheduler memoryTaskScheduler;

    @Mock
    StorageTransfer storageTransfer;

    @Mock
    AgentContext agentContext;

    @Mock
    ChatTools chatTools;

    @Mock
    cn.tolim.pudans.core.ai.library.command.SlashCommands slashCommands;

    @Mock
    cn.tolim.pudans.core.ai.library.hook.Hooks hooks;

    @Mock
    cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore journalStore;

    @Mock
    cn.tolim.pudans.core.ai.harness.agent.AgentStateStore stateStore;

    @Mock
    SubagentToolFactory subagentToolFactory;

    @Mock
    HarnessToolkitFactory harnessToolkitFactory;

    @Mock
    cn.tolim.pudans.core.ai.library.mcp.McpServers mcpServers;

    @Mock
    cn.tolim.pudans.core.ai.harness.observability.HarnessObservability harnessObservability;

    private ChatCompletions service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(journalStore.open(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new cn.tolim.pudans.core.ai.harness.journal.InMemoryThreadJournal()));
        service = new ChatCompletions(
                aiSettings,
                chatModes,
                teams,
                conversations,
                client,
                knowledgeBaseAcl,
                knowledgeRetrieval,
                embeddings,
                memoryContextBuilder,
                aiGateway,
                memoryTaskScheduler,
                storageTransfer,
                agentContext,
                chatTools,
                slashCommands,
                hooks,
                journalStore,
                stateStore,
                subagentToolFactory,
                harnessToolkitFactory,
                mcpServers,
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
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageTransfer.readBytes("file-1", 25 * 1024 * 1024))
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
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageTransfer.readBytes("file-1", 25 * 1024 * 1024))
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
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageTransfer.readBytes("file-1", 25 * 1024 * 1024))
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
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(imageFile("user-2")));

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
