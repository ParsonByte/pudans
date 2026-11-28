package cn.tolim.pudans.search.service;

import cn.tolim.pudans.search.config.SearchProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchSnippetGeneratorTests {

    private final SearchProperties properties = new SearchProperties();

    private SearchSnippetGenerator generator() {
        return new SearchSnippetGenerator(properties);
    }

    @Test
    void highlightsKeywordMatch() {
        String snippet = generator().generate("Refund policy", null,
                "Customers can request a refund within 30 days.", "refund");

        assertThat(snippet).isEqualTo("Customers can request a <em>refund</em> within 30 days.");
    }

    @Test
    void highlightsMatchCaseInsensitively() {
        String snippet = generator().generate(null, "REFUND policy", null, "refund");

        assertThat(snippet).isEqualTo("<em>REFUND</em> policy");
    }

    @Test
    void escapesHtmlAroundMatch() {
        String snippet = generator().generate(null, null,
                "<b>refund</b> & more", "refund");

        assertThat(snippet).isEqualTo("&lt;b&gt;<em>refund</em>&lt;/b&gt; &amp; more");
        assertThat(snippet).doesNotContain("<b>");
    }

    @Test
    void usesSummaryWhenContentDoesNotContainKeyword() {
        String snippet = generator().generate("Refund policy", "Refund requests are reviewed",
                "Unrelated content about payments", "refund");

        assertThat(snippet).contains("<em>Refund</em> requests are reviewed");
        assertThat(snippet).doesNotContain("Unrelated content");
    }

    @Test
    void usesTitleWhenOtherFieldsAreEmpty() {
        String snippet = generator().generate("Refund policy", null, null, "refund");

        assertThat(snippet).isEqualTo("<em>Refund</em> policy");
    }

    @Test
    void returnsEmptyStringWhenNoSourceFieldsExist() {
        assertThat(generator().generate(null, null, null, "refund")).isEmpty();
    }

    @Test
    void returnsTruncatedPrefixWithoutKeyword() {
        properties.setSnippetLength(10);

        String snippet = generator().generate(null, null,
                "0123456789abcdef", null);

        assertThat(snippet).isEqualTo("0123456789...");
    }

    @Test
    void addsEllipsisWhenWindowDoesNotCoverWholeField() {
        properties.setSnippetLength(40);
        String paddingBefore = "x".repeat(200);
        String paddingAfter = "y".repeat(200);

        String snippet = generator().generate(null, null,
                paddingBefore + " refund " + paddingAfter, "refund");

        assertThat(snippet).startsWith("...").endsWith("...").contains("<em>refund</em>");
        assertThat(snippet.length()).isEqualTo(55);
    }

    @Test
    void treatsRegexSpecialCharactersAsLiteralText() {
        String snippet = generator().generate(null, null, "cost is 100% now", "100%");

        assertThat(snippet).contains("<em>100%</em>");
    }

    @Test
    void keepsWholeLongKeywordInWindow() {
        properties.setSnippetLength(20);
        String keyword = "a very long search phrase";

        String snippet = generator().generate(null, null,
                "prefix " + keyword + " suffix", keyword);

        assertThat(snippet).contains("<em>" + keyword + "</em>");
    }
}