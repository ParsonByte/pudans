package cn.tolim.pudans.core.ai.harness.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolResultBlocksTest {

    @Test
    void extractsDataUrlsFromToolOutput() {
        var blocks = ToolResultBlocks.extract(
                "生成完成：data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst().mediaType()).isEqualTo("image/png");
        assertThat(blocks.getFirst().data()).isEqualTo("iVBORw0KGgoAAAANSUhEUg==");
    }

    @Test
    void extractsMediaJsonPayloads() {
        var blocks = ToolResultBlocks.extract(
                "{\"mediaType\":\"audio/mpeg\",\"data\":\"SUQzBAAAAAAA\"}");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst().mediaType()).isEqualTo("audio/mpeg");
        assertThat(blocks.getFirst().data()).isEqualTo("SUQzBAAAAAAA");
    }

    @Test
    void ignoresPlainTextAndVideoUrls() {
        assertThat(ToolResultBlocks.extract("done")).isEmpty();
        assertThat(ToolResultBlocks.extract("https://cdn.example.com/a.mp4")).isEmpty();
    }
}