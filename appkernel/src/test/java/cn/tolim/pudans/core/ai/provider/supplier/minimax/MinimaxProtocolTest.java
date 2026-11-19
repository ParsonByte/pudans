package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MinimaxProtocolTest {

    @Test
    void normalizesWebSocketPaths() {
        assertThat(MinimaxProtocol.normalizeWebSocketPath(""))
                .isEqualTo("/ws/v1/t2a_v2");
        assertThat(MinimaxProtocol.normalizeWebSocketPath("t2a_v2_bidi"))
                .isEqualTo("/ws/v1/t2a_v2_bidi");
        assertThat(MinimaxProtocol.normalizeWebSocketPath("/ws/v1/t2a_v2"))
                .isEqualTo("/ws/v1/t2a_v2");
        assertThatThrownBy(() -> MinimaxProtocol.normalizeWebSocketPath("../secrets"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
