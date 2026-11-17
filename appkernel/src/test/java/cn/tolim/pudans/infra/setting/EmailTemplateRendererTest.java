package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    void rendersSimpleVariablesForBackwardCompatibility() {
        assertThat(renderer.render("Hello {{name}}, code {{code}}", Map.of("name", "Alice", "code", "123456")))
                .isEqualTo("Hello Alice, code 123456");
    }

    @Test
    void rendersIfAndElseBlocks() {
        var template = "{{#if enabled}}on{{else}}off{{/if}}";

        assertThat(renderer.render(template, Map.of("enabled", true))).isEqualTo("on");
        assertThat(renderer.render(template, Map.of("enabled", false))).isEqualTo("off");
        assertThatThrownBy(() -> renderer.render(template, Map.of()))
                .isInstanceOf(TemplateRenderException.class)
                .hasMessageContaining("enabled");
    }

    @Test
    void rendersEachBlocksWithNestedFields() {
        var template = "{{#each users}}{{name}};{{/each}}";
        var users = List.of(Map.of("name", "Alice"), Map.of("name", "Bob"));

        assertThat(renderer.render(template, Map.of("users", users))).isEqualTo("Alice;Bob;");
    }

    @Test
    void rendersEachBlocksWithScalarItems() {
        assertThat(renderer.render("{{#each items}}{{this}},{{/each}}", Map.of("items", List.of("a", "b"))))
                .isEqualTo("a,b,");
    }

    @Test
    void appliesFilters() {
        assertThat(renderer.render("{{value | escape}}", Map.of("value", "<a&b>")))
                .isEqualTo("&lt;a&amp;b&gt;");
        assertThat(renderer.render("{{value | upper}}", Map.of("value", "abc"))).isEqualTo("ABC");
    }

    @Test
    void missingVariablesFailLoudly() {
        assertThatThrownBy(() -> renderer.render("Hello {{name}}", Map.of()))
                .isInstanceOf(TemplateRenderException.class)
                .hasMessageContaining("name");
    }

    @Test
    void unknownFiltersFailLoudly() {
        assertThatThrownBy(() -> renderer.render("{{name | unknown}}", Map.of("name", "Alice")))
                .isInstanceOf(TemplateRenderException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void nestedBlocksRender() {
        var template = "{{#each users}}{{#if active}}{{name}};{{/if}}{{/each}}";
        var users = List.of(Map.of("name", "Alice", "active", true), Map.of("name", "Bob", "active", false));

        assertThat(renderer.render(template, Map.of("users", users))).isEqualTo("Alice;");
    }
}
