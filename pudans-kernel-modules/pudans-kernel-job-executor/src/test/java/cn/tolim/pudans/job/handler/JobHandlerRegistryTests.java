package cn.tolim.pudans.job.handler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobHandlerRegistryTests {

    @Test
    void findsHandlersByName() {
        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(handler("alpha", "first"),
                handler("beta", "second")));

        assertThat(registry.names()).containsExactly("alpha", "beta");
        assertThat(registry.contains("alpha")).isTrue();
        assertThat(registry.contains("missing")).isFalse();
        assertThat(registry.find("beta")).get()
                .extracting(JobHandler::description).isEqualTo("second");
    }

    @Test
    void rejectsDuplicateHandlerNames() {
        assertThatThrownBy(() -> new JobHandlerRegistry(List.of(handler("alpha", "first"),
                handler("alpha", "second"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate job handler name 'alpha'");
    }

    @Test
    void supportsEmptyRegistry() {
        JobHandlerRegistry registry = new JobHandlerRegistry(List.of());

        assertThat(registry.names()).isEmpty();
        assertThat(registry.find("anything")).isEmpty();
        assertThat(registry.descriptors()).isEmpty();
    }

    @Test
    void exposesSortedDescriptors() {
        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(handler("beta", "second"),
                handler("alpha", "first")));

        assertThat(registry.descriptors())
                .extracting(descriptor -> descriptor.name())
                .containsExactly("alpha", "beta");
        assertThat(registry.descriptors().getFirst().handlerClass())
                .isEqualTo(TestHandler.class.getName());
    }

    private JobHandler handler(String name, String description) {
        return new TestHandler(name, description);
    }

    private static final class TestHandler implements JobHandler {

        private final String name;
        private final String description;

        private TestHandler(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public JobHandlerResult execute(JobExecutionContext context) {
            return JobHandlerResult.ok();
        }
    }
}