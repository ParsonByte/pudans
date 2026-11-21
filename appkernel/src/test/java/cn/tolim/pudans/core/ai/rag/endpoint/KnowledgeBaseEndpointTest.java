package cn.tolim.pudans.core.ai.rag.endpoint;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.KnowledgeBase;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.api.ListResult;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.core.ai.rag.DocumentStatus;
import cn.tolim.pudans.core.ai.rag.IngestionResult;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseDocument;
import cn.tolim.pudans.core.ai.rag.KnowledgeBases;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseQueryLog;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalResult;
import cn.tolim.pudans.core.ai.rag.RetrievalOptions;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseEndpointTest {

    private static final String USERNAME = "alice";

    private static final String UID = "uid-alice";

    private static final String AI_USER_ROLE = "role-template-ai-user";

    @Mock
    KnowledgeBases knowledgeBases;

    @Mock
    Accounts accounts;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new KnowledgeBaseEndpoint(knowledgeBases, accounts);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter(authenticatedAs(USERNAME, "ROLE_" + AI_USER_ROLE))
                .build();
    }

    @Test
    void listsReadableKnowledgeBases() {
        authenticatedUser();
        when(knowledgeBases.listVisible(UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Flux.just(knowledgeBase("kb-a")));

        client.get()
                .uri("/knowledgebases")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].metadata.name")
                .isEqualTo("kb-a")
                .jsonPath("$.total")
                .isEqualTo(1);
    }

    @Test
    void returnsKnowledgeBaseDetail() {
        authenticatedUser();
        when(knowledgeBases.findVisible("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.just(knowledgeBase("kb-a")));

        client.get()
                .uri("/knowledgebases/kb-a")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.metadata.name")
                .isEqualTo("kb-a");
    }

    @Test
    void hidesUnreadableKnowledgeBaseDetail() {
        authenticatedUser();
        when(knowledgeBases.findVisible("kb-private", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        KnowledgeBases.CODE_KB_NOT_FOUND,
                        "知识库不存在或无权访问。")));

        client.get()
                .uri("/knowledgebases/kb-private")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void rejectsSearchWithoutQuery() {
        client.get()
                .uri("/knowledgebases/-/search")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(knowledgeBases);
    }

    @Test
    void searchesWithDefaultOptions() {
        authenticatedUser();
        when(knowledgeBases.search(
                        UID,
                        Set.of(AI_USER_ROLE),
                        null,
                        "hello",
                        List.of(),
                        new RetrievalOptions(60, 0.3, 8, 4000)))
                .thenReturn(Mono.just(new KnowledgeBaseRetrievalResult("prompt", List.of())));

        client.get()
                .uri("/knowledgebases/-/search?q=hello")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.prompt")
                .isEqualTo("prompt");

        verify(knowledgeBases)
                .search(
                        UID,
                        Set.of(AI_USER_ROLE),
                        null,
                        "hello",
                        List.of(),
                        new RetrievalOptions(60, 0.3, 8, 4000));
    }

    @Test
    void searchesWithQueryOverrides() {
        authenticatedUser();
        when(knowledgeBases.search(
                        UID,
                        Set.of(AI_USER_ROLE),
                        null,
                        "hello",
                        List.of("kb-a", "kb-b"),
                        new RetrievalOptions(30, 0.5, 5, 1000)))
                .thenReturn(Mono.just(new KnowledgeBaseRetrievalResult("prompt", List.of())));

        client.get()
                .uri("/knowledgebases/-/search?q=hello&kbCodes=kb-a,kb-b&topK=5"
                        + "&scoreThreshold=0.5&rrfK=30&charBudget=1000")
                .exchange()
                .expectStatus()
                .isOk();

        verify(knowledgeBases)
                .search(
                        UID,
                        Set.of(AI_USER_ROLE),
                        null,
                        "hello",
                        List.of("kb-a", "kb-b"),
                        new RetrievalOptions(30, 0.5, 5, 1000));
    }

    @Test
    void rejectsInvalidSearchParameter() {
        client.get()
                .uri("/knowledgebases/-/search?q=hello&topK=0")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(knowledgeBases);
    }

    @Test
    void uploadsDocument() {
        authenticatedUser();
        var knowledgeBase = knowledgeBase("kb-a");
        var result = ingestionResult(9);
        when(knowledgeBases.findWritable("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.just(knowledgeBase));
        when(knowledgeBases.uploadDocument(knowledgeBase, UID, "file-1", "manual.pdf"))
                .thenReturn(Mono.just(result));

        client.post()
                .uri("/knowledgebases/kb-a/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new KnowledgeBaseEndpoint.DocumentUploadRequest("file-1", "manual.pdf"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.document.id")
                .isEqualTo(9);
    }

    @Test
    void rejectsForbiddenDocumentUpload() {
        authenticatedUser();
        var knowledgeBase = knowledgeBase("kb-a");
        when(knowledgeBases.findWritable("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.just(knowledgeBase));
        when(knowledgeBases.uploadDocument(knowledgeBase, UID, "file-1", null))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.FORBIDDEN,
                        KnowledgeBases.CODE_FORBIDDEN,
                        "存储文件不属于当前用户。")));

        client.post()
                .uri("/knowledgebases/kb-a/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new KnowledgeBaseEndpoint.DocumentUploadRequest("file-1", null))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void listsDocumentsWithFilters() {
        authenticatedUser();
        var knowledgeBase = knowledgeBase("kb-a");
        when(knowledgeBases.findReadable("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.just(knowledgeBase));
        when(knowledgeBases.listDocuments("kb-a", DocumentStatus.FAILED, 2, 10))
                .thenReturn(Mono.just(new ListResult<>(2, 10, 3, List.of(document(9)))));

        client.get()
                .uri("/knowledgebases/kb-a/documents?page=2&size=10&status=failed")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].id")
                .isEqualTo(9)
                .jsonPath("$.total")
                .isEqualTo(3);
    }

    @Test
    void returnsDocumentDetail() {
        authenticatedUser();
        var knowledgeBase = knowledgeBase("kb-a");
        when(knowledgeBases.findReadable("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.just(knowledgeBase));
        when(knowledgeBases.findDocument("kb-a", 9)).thenReturn(Mono.just(document(9)));

        client.get()
                .uri("/knowledgebases/kb-a/documents/9")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(9);
    }

    @Test
    void deletesDocument() {
        authenticatedUser();
        var knowledgeBase = knowledgeBase("kb-a");
        when(knowledgeBases.findWritable("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.just(knowledgeBase));
        when(knowledgeBases.deleteDocument("kb-a", 9)).thenReturn(Mono.empty());

        client.delete()
                .uri("/knowledgebases/kb-a/documents/9")
                .exchange()
                .expectStatus()
                .isNoContent();

        verify(knowledgeBases).deleteDocument("kb-a", 9);
    }

    @Test
    void hidesDocumentDeleteWithoutWriteAccess() {
        authenticatedUser();
        when(knowledgeBases.findWritable("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        KnowledgeBases.CODE_KB_NOT_FOUND,
                        "知识库不存在或无权访问。")));

        client.delete()
                .uri("/knowledgebases/kb-a/documents/9")
                .exchange()
                .expectStatus()
                .isNotFound();

        verify(knowledgeBases, never()).deleteDocument("kb-a", 9);
    }

    @Test
    void rejectsInvalidDocumentId() {
        client.delete()
                .uri("/knowledgebases/kb-a/documents/not-a-number")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(knowledgeBases);
    }

    @Test
    void retriesFailedDocument() {
        authenticatedUser();
        var knowledgeBase = knowledgeBase("kb-a");
        when(knowledgeBases.findWritable("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.just(knowledgeBase));
        when(knowledgeBases.retryDocument(knowledgeBase, 9, UID)).thenReturn(Mono.just(document(9)));

        client.post()
                .uri("/knowledgebases/kb-a/documents/9/retry")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(9);

        verify(knowledgeBases).retryDocument(knowledgeBase, 9, UID);
    }

    @Test
    void reindexesKnowledgeBase() {
        authenticatedUser();
        var knowledgeBase = knowledgeBase("kb-a");
        when(knowledgeBases.findWritable("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.just(knowledgeBase));
        when(knowledgeBases.reindex(knowledgeBase, UID)).thenReturn(Mono.just(2L));

        client.post()
                .uri("/knowledgebases/kb-a/reindex")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.ACCEPTED)
                .expectBody()
                .jsonPath("$.queued")
                .isEqualTo(2);

        verify(knowledgeBases).reindex(knowledgeBase, UID);
    }

    @Test
    void listsQueryLogs() {
        authenticatedUser();
        var knowledgeBase = knowledgeBase("kb-a");
        var queryLog = new KnowledgeBaseQueryLog();
        queryLog.setQuery("hello");
        when(knowledgeBases.findReadable("kb-a", UID, Set.of(AI_USER_ROLE), null))
                .thenReturn(Mono.just(knowledgeBase));
        when(knowledgeBases.listQueryLogs("kb-a", 50)).thenReturn(Flux.just(queryLog));

        client.get()
                .uri("/knowledgebases/kb-a/querylogs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].query")
                .isEqualTo("hello");

        verify(knowledgeBases).listQueryLogs("kb-a", 50);
    }

    private void authenticatedUser() {
        var user = new User();
        user.getSpec().setUid(UID);
        user.getSpec().setDisplayName(USERNAME);
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user));
    }

    private static WebFilter authenticatedAs(String username, String... authorities) {
        var grantedAuthorities = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(username, "n/a", grantedAuthorities);
        return (exchange, chain) -> chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private static KnowledgeBase knowledgeBase(String name) {
        var knowledgeBase = new KnowledgeBase();
        var metadata = new Metadata();
        metadata.setName(name);
        knowledgeBase.setMetadata(metadata);
        knowledgeBase.getSpec().setDisplayName(name);
        knowledgeBase.getSpec().setVisibility(KnowledgeBase.Visibility.COMPANY);
        return knowledgeBase;
    }

    private static KnowledgeBaseDocument document(long id) {
        var document = new KnowledgeBaseDocument();
        document.setId(id);
        document.setKbName("kb-a");
        document.setFileName("manual.md");
        document.setStatus(DocumentStatus.READY);
        return document;
    }

    private static IngestionResult ingestionResult(long id) {
        return new IngestionResult(document(id), false);
    }
}
