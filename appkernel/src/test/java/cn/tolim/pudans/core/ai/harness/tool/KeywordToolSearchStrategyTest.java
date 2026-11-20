package cn.tolim.pudans.core.ai.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordToolSearchStrategyTest {

    private final KeywordToolSearchStrategy strategy = new KeywordToolSearchStrategy();

    @Test
    void ranksNameMatchesAboveDescriptionMatches() {
        var request = new ToolSearchRequest(
                List.of(
                        specification("translate_text", "翻译文本"),
                        specification("summarize", "text summarizer"),
                        specification("fetch_weather", "查询城市天气")),
                "text",
                10);

        var result = strategy.search(request);

        assertThat(result.toolNames()).containsExactly("translate_text", "summarize");
    }

    @Test
    void matchesCaseInsensitivelyAndAppliesTheLimit() {
        var request = new ToolSearchRequest(
                List.of(
                        specification("fetch_weather", "查询城市天气"),
                        specification("weather_alerts", "天气预警"),
                        specification("weather", "天气查询")),
                "WEATHER",
                2);

        var result = strategy.search(request);

        assertThat(result.toolNames()).containsExactly("fetch_weather", "weather");
    }

    @Test
    void matchesChineseTermsAgainstDescriptions() {
        var request = new ToolSearchRequest(
                List.of(
                        specification("fetch_weather", "查询城市天气"),
                        specification("translate_text", "翻译文本")),
                "天气",
                5);

        var result = strategy.search(request);

        assertThat(result.toolNames()).containsExactly("fetch_weather");
    }

    @Test
    void returnsAnEmptyResultWhenNothingMatches() {
        var request = new ToolSearchRequest(
                List.of(specification("fetch_weather", "查询城市天气")),
                "calendar",
                5);

        assertThat(strategy.search(request).toolNames()).isEmpty();
    }

    @Test
    void rejectsBlankQueriesAndNonPositiveLimits() {
        var candidates = List.of(specification("fetch_weather", "查询城市天气"));

        assertThatThrownBy(() -> new ToolSearchRequest(candidates, "  ", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolSearchRequest(candidates, "weather", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ToolSpecification specification(String name, String description) {
        return ToolSpecification.builder().name(name).description(description).build();
    }
}