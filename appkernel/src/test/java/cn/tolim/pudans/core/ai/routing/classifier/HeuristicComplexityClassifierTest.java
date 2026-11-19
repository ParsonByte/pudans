package cn.tolim.pudans.core.ai.routing.classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HeuristicComplexityClassifierTest {

    private final HeuristicComplexityClassifier classifier = new HeuristicComplexityClassifier();

    @Test
    void classifiesShortPromptAsSimple() {
        var verdict = classifier.classify(new ComplexityInput(null, 100));

        assertThat(verdict.level()).isEqualTo(ComplexityLevel.SIMPLE);
        assertThat(verdict.confidence()).isEqualTo(0.8);
    }

    @Test
    void classifiesMediumPromptAsMedium() {
        var verdict = classifier.classify(new ComplexityInput(null, 1000));

        assertThat(verdict.level()).isEqualTo(ComplexityLevel.MEDIUM);
        assertThat(verdict.confidence()).isEqualTo(0.7);
    }

    @Test
    void classifiesLongPromptAsComplex() {
        var verdict = classifier.classify(new ComplexityInput(null, 5000));

        assertThat(verdict.level()).isEqualTo(ComplexityLevel.COMPLEX);
        assertThat(verdict.confidence()).isEqualTo(0.85);
    }

    @Test
    void estimatesLatinTextWhenTokenCountIsMissing() {
        var verdict = classifier.classify(new ComplexityInput("a".repeat(4000), 0));

        assertThat(verdict.level()).isEqualTo(ComplexityLevel.MEDIUM);
    }

    @Test
    void estimatesChineseTextWhenTokenCountIsMissing() {
        var verdict = classifier.classify(new ComplexityInput("汉".repeat(600), 0));

        assertThat(verdict.level()).isEqualTo(ComplexityLevel.MEDIUM);
    }

    @Test
    void rejectsNegativePromptTokens() {
        assertThatThrownBy(() -> new ComplexityInput(null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
