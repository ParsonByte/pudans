package cn.tolim.pudans.core.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationsTest {

    @Mock
    TextGenerations textGenerations;

    @Mock
    StorageTransfer storageTransfer;

    private DocumentGenerations service;

    @BeforeEach
    void setUp() {
        service = new DocumentGenerations(textGenerations, storageTransfer);
    }

    @Test
    void storesMarkdownOutputAndStripsCodeFences() {
        when(textGenerations.generate(any()))
                .thenReturn(Mono.just(new GenerationResult("```markdown\n# 标题\n正文\n```", "gpt-test", "p", "a")));
        var file = storageFile("file-1");
        when(storageTransfer.upload(any())).thenReturn(Mono.just(file));
        var captor = ArgumentCaptor.forClass(StorageWriteRequest.class);

        StepVerifier.create(service.generate(new DocumentGenerations.Command(
                        "user-1", "季度报告", "写一份季度报告", DocumentGenerations.Format.MARKDOWN, null)))
                .assertNext(result -> {
                    assertThat(result.fileId()).isEqualTo("file-1");
                    assertThat(result.name()).isEqualTo("季度报告.md");
                    assertThat(result.mimeType()).isEqualTo("text/markdown");
                })
                .verifyComplete();

        verify(storageTransfer).upload(captor.capture());
        var request = captor.getValue();
        assertThat(request.owner()).isEqualTo("user-1");
        assertThat(request.originalName()).isEqualTo("季度报告.md");
        assertThat(read(request.body())).isEqualTo("# 标题\n正文");
    }

    @Test
    void generatesHtmlWithTheHtmlExtension() {
        when(textGenerations.generate(any()))
                .thenReturn(Mono.just(new GenerationResult("<html><body>内容</body></html>", "gpt-test", "p", "a")));
        var file = storageFile("file-2");
        when(storageTransfer.upload(any())).thenReturn(Mono.just(file));
        var captor = ArgumentCaptor.forClass(StorageWriteRequest.class);

        StepVerifier.create(service.generate(new DocumentGenerations.Command(
                        "user-1", "页面", "生成页面", DocumentGenerations.Format.HTML, "pro-group")))
                .assertNext(result -> {
                    assertThat(result.name()).isEqualTo("页面.html");
                    assertThat(result.mimeType()).isEqualTo("text/html");
                })
                .verifyComplete();

        verify(storageTransfer).upload(captor.capture());
        assertThat(captor.getValue().attributes()).containsEntry("format", "HTML");
        assertThat(read(captor.getValue().body())).contains("<html>");
    }

    @Test
    void rejectsABlankPrompt() {
        StepVerifier.create(service.generate(new DocumentGenerations.Command(
                        "user-1", "标题", " ", null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("ai-document-generation-invalid");
                })
                .verify();
    }

    private static StorageFile storageFile(String name) {
        var file = mock(StorageFile.class);
        var metadata = new Metadata();
        metadata.setName(name);
        when(file.getMetadata()).thenReturn(metadata);
        return file;
    }

    private static String read(reactor.core.publisher.Flux<DataBuffer> body) {
        return DataBufferUtils.join(body)
                .map(buffer -> {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .block();
    }
}