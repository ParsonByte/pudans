package cn.tolim.pudans.mq.consumer;

import cn.tolim.pudans.mq.model.ConsumerDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageConsumerRegistryTests {

    private MessageConsumerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MessageConsumerRegistry(emptyProvider());
    }

    @Test
    void discoversConsumersByExactTopicAndWildcard() {
        registry.register(consumer("billing", "order.created"));
        registry.register(consumer("audit", "*"));
        registry.register(consumer("catalog", "product.updated"));

        List<MessageConsumer> consumers = registry.consumersFor("order.created");

        assertThat(consumers).extracting(MessageConsumer::consumerGroup)
                .containsExactly("audit", "billing");
        assertThat(registry.consumersFor("unknown.topic"))
                .extracting(MessageConsumer::consumerGroup)
                .containsExactly("audit");
    }

    @Test
    void describesRegisteredConsumersInGroupOrder() {
        registry.register(consumer("billing", "order.created", "order.refunded"));
        registry.register(consumer("audit", "*"));

        List<ConsumerDescriptor> descriptors = registry.descriptors();

        assertThat(descriptors).extracting(ConsumerDescriptor::consumerGroup)
                .containsExactly("audit", "billing");
        assertThat(descriptors.getLast().topics())
                .containsExactlyInAnyOrder("order.created", "order.refunded");
    }

    @Test
    void rejectsDuplicateConsumerGroup() {
        registry.register(consumer("billing", "order.created"));

        assertThatThrownBy(() -> registry.register(consumer("billing", "order.created")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("billing");
    }

    @Test
    void rejectsConsumerWithoutTopics() {
        MessageConsumer consumer = consumer("billing");

        assertThatThrownBy(() -> registry.register(consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one topic");
    }

    @Test
    void registersConsumersProvidedBySpring() {
        MessageConsumer billing = consumer("billing", "order.created");
        ObjectProvider<MessageConsumer> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(Stream.of(billing));

        MessageConsumerRegistry providedRegistry = new MessageConsumerRegistry(provider);

        assertThat(providedRegistry.size()).isEqualTo(1);
        assertThat(providedRegistry.consumersFor("order.created")).containsExactly(billing);
    }

    private MessageConsumer consumer(String group, String... topics) {
        MessageConsumer consumer = mock(MessageConsumer.class);
        when(consumer.consumerGroup()).thenReturn(group);
        when(consumer.topics()).thenReturn(Set.of(topics));
        return consumer;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MessageConsumer> emptyProvider() {
        ObjectProvider<MessageConsumer> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(Stream.empty());
        return provider;
    }
}