package cn.tolim.pudans.core.infra.r2dbc.ai.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VectorCodecTest {

    @Test
    void formatsAndParsesAVector() {
        var vector = new float[] {0.1f, -0.2f, 1.25f};

        var text = VectorCodec.format(vector);

        assertThat(text).isEqualTo("[0.1,-0.2,1.25]");
        assertThat(VectorCodec.parse(text)).containsExactly(vector);
    }

    @Test
    void parsesAnEmptyVector() {
        assertThat(VectorCodec.parse("[]")).isEmpty();
        assertThat(VectorCodec.parse("")).isEmpty();
    }

    @Test
    void rejectsInvalidShape() {
        assertThatThrownBy(() -> VectorCodec.parse("0.1,0.2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesTheDimension() {
        assertThatThrownBy(() -> VectorCodec.requireDimension(new float[] {0.1f}, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("期望 3");
    }
}
