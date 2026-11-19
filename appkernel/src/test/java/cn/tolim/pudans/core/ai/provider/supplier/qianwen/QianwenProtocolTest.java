package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QianwenProtocolTest {

    @Test
    void normalizesDashScopePaths() {
        assertThat(QianwenProtocol.normalizePath("/services/aigc/text-generation/generation"))
                .isEqualTo("/api/v1/services/aigc/text-generation/generation");
        assertThat(QianwenProtocol.normalizePath("api/v1/tasks/task-1"))
                .isEqualTo("/api/v1/tasks/task-1");
        assertThat(QianwenProtocol.normalizePath("api/v2/apps/world/openapi/v1/worlds"))
                .isEqualTo("/api/v2/apps/world/openapi/v1/worlds");
    }

    @Test
    void normalizesCompatibleModePaths() {
        assertThat(QianwenProtocol.normalizePath("compatible-mode/v1/files"))
                .isEqualTo("/compatible-mode/v1/files");
        assertThat(QianwenProtocol.normalizePath("files"))
                .isEqualTo("/compatible-mode/v1/files");
        assertThat(QianwenProtocol.normalizePath("batches"))
                .isEqualTo("/compatible-mode/v1/batches");
        assertThat(QianwenProtocol.normalizePath("responses"))
                .isEqualTo("/compatible-mode/v1/responses");
        assertThat(QianwenProtocol.normalizePath("reranks"))
                .isEqualTo("/compatible-api/v1/reranks");
    }

    @Test
    void normalizesWebSocketPaths() {
        assertThat(QianwenProtocol.normalizeWebSocketPath("realtime"))
                .isEqualTo("/api-ws/v1/realtime");
        assertThat(QianwenProtocol.normalizeWebSocketPath("/api-ws/v1/inference"))
                .isEqualTo("/api-ws/v1/inference");
        assertThat(QianwenProtocol.normalizeWebSocketPath(""))
                .isEqualTo("/api-ws/v1/realtime");
        assertThatThrownBy(() -> QianwenProtocol.normalizeWebSocketPath("../secrets"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsafePaths() {
        assertThatThrownBy(() -> QianwenProtocol.normalizePath("../secrets"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QianwenProtocol.normalizePath("https://evil.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QianwenProtocol.normalizePath("services//x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QianwenProtocol.normalizePath(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
