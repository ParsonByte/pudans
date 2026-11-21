package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.storage.resource.StorageFileState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.provider.AiProviderFile;
import cn.tolim.pudans.core.ai.provider.AiProviderFilePage;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekFilesClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.infra.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiProviderFileEndpointTest {

    @Mock
    DeepSeekProviderAccess access;

    @Mock
    DeepSeekFilesClient filesClient;

    @Mock
    StorageTransfer storageTransfer;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var endpoint = new AiProviderFileEndpoint(access, filesClient, storageTransfer);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void uploadsStoredFile() {
        var provider = provider();
        var account = account();
        var file = storedFile();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(storageTransfer.getFile("storage-1")).thenReturn(Mono.just(file));
        when(storageTransfer.readBytes(eq("storage-1"), anyInt()))
                .thenReturn(Mono.just("hello".getBytes(StandardCharsets.UTF_8)));
        when(filesClient.upload(
                        eq(provider),
                        eq(account),
                        eq("report.pdf"),
                        eq("application/pdf"),
                        any(),
                        eq("user_data"),
                        eq(3600L)))
                .thenReturn(Mono.just(providerFile()));

        webClient.post()
                .uri("/aiproviders/provider-a/files")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "storageFileId", "storage-1",
                        "purpose", "user_data",
                        "expiresAfterSeconds", 3600))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("file-api-1")
                .jsonPath("$.filename").isEqualTo("report.pdf")
                .jsonPath("$.purpose").isEqualTo("user_data");
    }

    @Test
    void rejectsUnsupportedProvider() {
        when(access.requireProvider("provider-a")).thenReturn(Mono.error(new ApiException(
                HttpStatus.BAD_REQUEST, "ai-deepseek-provider-unsupported", "not deepseek")));

        webClient.get()
                .uri("/aiproviders/provider-a/files")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsProviderWithoutEnabledAccount() {
        var provider = provider();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.error(new ApiException(
                HttpStatus.BAD_GATEWAY, "ai-deepseek-no-account", "no account")));

        webClient.get()
                .uri("/aiproviders/provider-a/files")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void listsFiles() {
        var provider = provider();
        var account = account();
        var page = new AiProviderFilePage(
                "list", List.of(providerFile()), "file-api-1", "file-api-1", true);
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(filesClient.list(provider, account, 5, "desc", "file-api-1", "user_data"))
                .thenReturn(Mono.just(page));

        webClient.get()
                .uri("/aiproviders/provider-a/files?limit=5&order=desc&after=file-api-1&purpose=user_data")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].id").isEqualTo("file-api-1")
                .jsonPath("$.hasMore").isEqualTo(true);
    }

    @Test
    void getsFile() {
        var provider = provider();
        var account = account();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(filesClient.get(provider, account, "file-api-1")).thenReturn(Mono.just(providerFile()));

        webClient.get()
                .uri("/aiproviders/provider-a/files/file-api-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.purpose").isEqualTo("user_data");
    }

    @Test
    void deletesFile() {
        var provider = provider();
        var account = account();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(filesClient.delete(provider, account, "file-api-1")).thenReturn(Mono.empty());

        webClient.delete()
                .uri("/aiproviders/provider-a/files/file-api-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void rejectsMissingStorageFileId() {
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider()));

        webClient.post()
                .uri("/aiproviders/provider-a/files")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("purpose", "user_data"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsInvalidPurpose() {
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider()));

        webClient.post()
                .uri("/aiproviders/provider-a/files")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("storageFileId", "storage-1", "purpose", "invalid"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsInactiveStorageFile() {
        var provider = provider();
        var file = storedFile();
        file.getSpec().setState(StorageFileState.TRASHED);
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(storageTransfer.getFile("storage-1")).thenReturn(Mono.just(file));

        webClient.post()
                .uri("/aiproviders/provider-a/files")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("storageFileId", "storage-1", "purpose", "user_data"))
                .exchange()
                .expectStatus().isNotFound();
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        provider.getSpec().setDisplayName("DeepSeek");
        provider.getSpec().setProtocol(AiProvider.Protocol.DEEPSEEK);
        provider.getSpec().setBaseUrl("https://api.deepseek.com");
        return provider;
    }

    private static AiProviderAccount account() {
        var account = new AiProviderAccount();
        account.setMetadata(metadata("account-a"));
        account.getSpec().setProvider("provider-a");
        return account;
    }

    private static StorageFile storedFile() {
        var file = new StorageFile();
        file.setMetadata(metadata("storage-1"));
        file.getSpec().setPolicy("default");
        file.getSpec().setStorageKey("key");
        file.getSpec().setOriginalName("report.pdf");
        file.getSpec().setContentType("application/pdf");
        file.getSpec().setState(StorageFileState.ACTIVE);
        return file;
    }

    private static AiProviderFile providerFile() {
        return new AiProviderFile(
                "file-api-1", "file", 5L, 1700000000L, "report.pdf", "user_data", null);
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
