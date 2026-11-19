package cn.tolim.pudans.core.ai.routing.classifier;

import java.util.Objects;

public class HeuristicComplexityClassifier implements ComplexityClassifier {

    private static final int SIMPLE_TOKEN_LIMIT = 512;

    private static final int MEDIUM_TOKEN_LIMIT = 2048;

    private static final int LATIN_CHARS_PER_TOKEN = 4;

    private static final double SIMPLE_CONFIDENCE = 0.8;

    private static final double MEDIUM_CONFIDENCE = 0.7;

    private static final double COMPLEX_CONFIDENCE = 0.85;

    @Override
    public ComplexityVerdict classify(ComplexityInput input) {
        Objects.requireNonNull(input, "input");
        int tokens = input.promptTokens() > 0 ? input.promptTokens() : estimateTokens(input.lastUserMessage());
        if (tokens <= SIMPLE_TOKEN_LIMIT) {
            return new ComplexityVerdict(ComplexityLevel.SIMPLE, SIMPLE_CONFIDENCE);
        }
        if (tokens <= MEDIUM_TOKEN_LIMIT) {
            return new ComplexityVerdict(ComplexityLevel.MEDIUM, MEDIUM_CONFIDENCE);
        }
        return new ComplexityVerdict(ComplexityLevel.COMPLEX, COMPLEX_CONFIDENCE);
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int latin = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                cjk++;
            } else {
                latin++;
            }
        }
        return cjk + (latin + LATIN_CHARS_PER_TOKEN - 1) / LATIN_CHARS_PER_TOKEN;
    }
}
