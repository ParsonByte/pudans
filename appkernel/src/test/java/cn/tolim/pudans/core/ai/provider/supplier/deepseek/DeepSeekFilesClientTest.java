package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiProviderFilePage;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class DeepSeekFilesClientTest {

    private static final String FILE_BODY = """
            {
              "id": "file-api-1",
              "object": "file",
              "bytes": 12345,
              "created_at": 1700000000,
              "filename": "report.pdf",
              "purpose": "user_data",
              "expires_at": 1700003600
            }
            """;

    private static final String PAGE_BODY = """
            {
              "object": "list",
              "data": [
                {"id": "file-api-1", "object": "file", "bytes": 10, "created_at": 1700000000,
                 "filename": "a.pdf", "purpose": "user_data"},
                {"id": "file-api-2", "object": "file", "bytes": 20, "created_at": 1700000100,
                 "filename": "b.png", "purpose": "text_recognition"}
              ],
              "first_id": "file-api-1",
              "last_id": "file-api-2",
              "has_more": true
            }
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void uploadsMultipartFile() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var file = client(json(FILE_BODY))
                .upload(provider(AiProvider.Protocol.DEEPSEEK), account(), "report.pdf",
                        "application/pdf", "hello".getBytes(StandardCharsets.UTF_8),
                        "rich_layout_recognition", 3600L)
                .block();

        assertThat(file).isNotNull();
        assertThat(file.id()).isEqualTo("file-api-1");
        assertThat(file.object()).isEqualTo("file");
        assertThat(file.bytes()).isEqualTo(12345L);
        assertThat(file.createdAt()).isEqualTo(1700000000L);
        assertThat(file.filename()).isEqualTo("report.pdf");
        assertThat(file.purpose()).isEqualTo("user_data");
        assertThat(file.expiresAt()).isEqualTo(1700003600L);

        var request = captured.get();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url().toString()).isEqualTo("https://api.example.com/files");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-ds-secret");
        assertThat(request.headers().containsHeader(DeepSeekProtocol.BETA_HEADER)).isFalse();
        var multipart = body(request);
        assertThat(multipart)
                .contains("name=\"purpose\"")
                .contains("rich_layout_recognition")
                .contains("name=\"expires_after[anchor]\"")
                .contains("created_at")
                .contains("name=\"expires_after[seconds]\"")
                .contains("3600")
                .contains("name=\"file\"")
                .contains("filename=\"report.pdf\"")
                .contains("hello");
    }

    @Test
    void listsFilesWithPagination() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        AiProviderFilePage page = client(json(PAGE_BODY))
                .list(provider(AiProvider.Protocol.DEEPSEEK), account(), 5, "desc", "file-api-1", "user_data")
                .block();

        assertThat(page).isNotNull();
        assertThat(page.object()).isEqualTo("list");
        assertThat(page.data()).hasSize(2);
        assertThat(page.data().get(1).id()).isEqualTo("file-api-2");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.firstId()).isEqualTo("file-api-1");
        assertThat(page.lastId()).isEqualTo("file-api-2");

        var url = captured.get().url().toString();
        assertThat(url).startsWith("https://api.example.com/files?");
        assertThat(url)
                .contains("after=file-api-1")
                .contains("limit=5")
                .contains("order=desc")
                .contains("purpose=user_data");
    }

    @Test
    void getsFile() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var file = client(json(FILE_BODY))
                .get(provider(AiProvider.Protocol.DEEPSEEK), account(), "file-api-1")
                .block();

        assertThat(file).isNotNull();
        assertThat(file.id()).isEqualTo("file-api-1");
        assertThat(captured.get().method()).isEqualTo(HttpMethod.GET);
        assertThat(captured.get().url().toString())
                .isEqualTo("https://api.example.com/files/file-api-1");
    }

    @Test
    void deletesFile() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        client(json("{\"id\":\"file-api-1\",\"object\":\"file\",\"deleted\":true}"))
                .delete(provider(AiProvider.Protocol.DEEPSEEK), account(), "file-api-1")
                .block();

        assertThat(captured.get().method()).isEqualTo(HttpMethod.DELETE);
        assertThat(captured.get().url().toString())
                .isEqualTo("https://api.example.com/files/file-api-1");
    }

    @Test
    void mapsUpstreamErrors() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));
        var response = ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":{\"message\":\"file invalid\"}}")
                .build();

        assertThatThrownBy(() -> client(response)
                        .get(provider(AiProvider.Protocol.DEEPSEEK), account(), "file-api-1")
                        .block())
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo(DeepSeekFilesClient.UPSTREAM_ERROR_CODE);
                    assertThat(error.getReason()).contains("file invalid");
                });
    }

    @Test
    void rejectsFileResponseWithoutId() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        assertThatThrownBy(() -> client(json("{\"filename\":\"a.pdf\"}"))
                        .get(provider(AiProvider.Protocol.DEEPSEEK), account(), "file-api-1")
                        .block())
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(error.getCode()).isEqualTo(DeepSeekFilesClient.UPSTREAM_ERROR_CODE);
                });
    }

    private DeepSeekFilesClient client(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new DeepSeekFilesClient(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
