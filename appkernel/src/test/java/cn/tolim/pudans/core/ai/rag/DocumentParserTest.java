package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DocumentParserTest {

    @Test
    void parsesAsynchronouslyOnABoundedElasticThread() {
        var threadName = new AtomicReference<String>();
        var parser = new DocumentParser() {

            @Override
            public boolean supports(String fileName, @Nullable String mimeType) {
                return true;
            }

            @Override
            public ParsedDocument parse(byte[] content) {
                threadName.set(Thread.currentThread().getName());
                return ParsedDocument.of("ok");
            }
        };

        StepVerifier.create(parser.parseAsync(new byte[0]))
                .assertNext(document -> assertThat(document.text()).isEqualTo("ok"))
                .verifyComplete();

        assertThat(threadName.get()).startsWith("boundedElastic-");
    }
}