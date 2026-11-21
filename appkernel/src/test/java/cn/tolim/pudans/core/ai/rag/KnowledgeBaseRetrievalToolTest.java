package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jToolMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseRetrievalToolTest {

    @Mock
    KnowledgeRetrieval knowledgeRetrieval;

    @Mock
    Embeddings embeddings;

    @BeforeEach
    void setUp() {
        lenient()
                .when(embeddings.embedOne(anyString()))
                .thenReturn(Mono.just(new float[] {0.1f, 0.2f}));
    }

    @Test
    void definesTheToolNameAndRequiredQuerySchema() {
        var definition = new KnowledgeBaseRetrievalTool(knowledgeRetrieval, embeddings, context()).definition();

        assertThat(definition.name()).isEqualTo(KnowledgeBaseRetrievalTool.NAME);
        assertThat(definition.parameters().path("type").asText()).isEqualTo("object");
        assertThat(definition.parameters().path("properties").has("query")).isTrue();
        assertThat(definition.parameters().path("properties").has("limit")).isTrue();
        assertThat(definition.parameters().path("required"))
                .extracting(node -> node.asText())
                .containsExactly("query");
    }

    @Test
    void mapsArgumentsToTheRetrievalRequest() {
        when(knowledgeRetrieval.retrieve(any())).thenReturn(Mono.just(result()));

        var outcome = handler().execute(
                request("{\"query\":\" 安装 \",\"limit\":3}"),
                LangChain4jToolMapper.arguments("{\"query\":\" 安装 \",\"limit\":3}"));

        assertThat(outcome.error()).isFalse();
        assertThat(outcome.output()).contains("[1]《manual.pdf》");
        var captor = ArgumentCaptor.forClass(KnowledgeBaseRetrievalRequest.class);
        verify(knowledgeRetrieval).retrieve(captor.capture());
        var request = captor.getValue();
        assertThat(request.userId()).isEqualTo("alice");
        assertThat(request.requestId()).isEqualTo("req-1");
        assertThat(request.query()).isEqualTo("安装");
        assertThat(request.roleNames()).containsExactly("member");
        assertThat(request.deptId()).isEqualTo("dept-a");
        assertThat(request.knowledgeBases()).hasSize(1);
        assertThat(request.queryEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(request.options().topK()).isEqualTo(3);
        assertThat(request.options().rrfK()).isEqualTo(60);
        assertThat(request.candidateLimit()).isEqualTo(24);
    }

    @Test
    void clampsTheLimitToTheSupportedRange() {
        when(knowledgeRetrieval.retrieve(any())).thenReturn(Mono.just(result()));

        handler().execute(request("{\"query\":\"安装\",\"limit\":99}"), LangChain4jToolMapper.arguments(
                "{\"query\":\"安装\",\"limit\":99}"));

        var captor = ArgumentCaptor.forClass(KnowledgeBaseRetrievalRequest.class);
        verify(knowledgeRetrieval).retrieve(captor.capture());
        assertThat(captor.getValue().options().topK()).isEqualTo(20);
    }

    @Test
    void rejectsAMissingQueryWithoutRetrieval() {
        var outcome = handler().execute(request("{}"), LangChain4jToolMapper.arguments("{}"));

        assertThat(outcome.error()).isTrue();
        assertThat(outcome.output()).contains("检索问题");
        verifyNoInteractions(knowledgeRetrieval);
    }

    @Test
    void reportsAnEmptyKnowledgeBaseWithoutError() {
        when(knowledgeRetrieval.retrieve(any()))
                .thenReturn(Mono.just(new KnowledgeBaseRetrievalResult("", List.of())));

        var outcome = handler().execute(
                request("{\"query\":\"安装\"}"), LangChain4jToolMapper.arguments("{\"query\":\"安装\"}"));

        assertThat(outcome.error()).isFalse();
        assertThat(outcome.output()).contains("未检索到");
    }

    @Test
    void reportsRetrievalFailuresAsToolErrors() {
        when(knowledgeRetrieval.retrieve(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        var outcome = handler().execute(
                request("{\"query\":\"安装\"}"), LangChain4jToolMapper.arguments("{\"query\":\"安装\"}"));

        assertThat(outcome.error()).isTrue();
        assertThat(outcome.output()).isEqualTo("db down");
    }

    @Test
    void registersWithTheHarnessToolkitAndExecutesThroughIt() {
        when(knowledgeRetrieval.retrieve(any())).thenReturn(Mono.just(result()));
        var toolkit = new KnowledgeBaseRetrievalTool(knowledgeRetrieval, embeddings, context())
                .register(new HarnessToolkit());

        var outcome = toolkit.execute(ToolExecutionRequest.builder()
                .name(KnowledgeBaseRetrievalTool.NAME)
                .arguments("{\"query\":\"安装\"}")
                .build());

        assertThat(outcome.error()).isFalse();
        assertThat(outcome.output()).contains("[1]《manual.pdf》");
    }

    @Test
    void exposesCitationsAfterRetrieval() {
        when(knowledgeRetrieval.retrieve(any())).thenReturn(Mono.just(result()));
        var tool = new KnowledgeBaseRetrievalTool(knowledgeRetrieval, embeddings, context());

        tool.handler().execute(
                request("{\"query\":\"安装\"}"), LangChain4jToolMapper.arguments("{\"query\":\"安装\"}"));

        assertThat(tool.citations()).isEqualTo(result().citations());
    }

    private HarnessToolkit.ToolHandler handler() {
        return new KnowledgeBaseRetrievalTool(knowledgeRetrieval, embeddings, context()).handler();
    }

    private static ToolExecutionRequest request(String arguments) {
        return ToolExecutionRequest.builder()
                .name(KnowledgeBaseRetrievalTool.NAME)
                .arguments(arguments)
                .build();
    }

    private static RagContext context() {
        var knowledgeBase = new KnowledgeBase();
        var metadata = new Metadata();
        metadata.setName("/registry/pudans.tolim.cn/knowledgebases/kb-a");
        knowledgeBase.setMetadata(metadata);
        return new RagContext(
                "alice",
                "req-1",
                List.of(knowledgeBase),
                Set.of("member"),
                "dept-a",
                new RetrievalOptions(60, 0.3, 8, 4000),
                24);
    }

    private static KnowledgeBaseRetrievalResult result() {
        var citation = new Citation(1, 101L, "manual.pdf", 1, "第一章", 0.5, "安装说明");
        return new KnowledgeBaseRetrievalResult("[1]《manual.pdf》\n安装说明", List.of(citation));
    }
}
