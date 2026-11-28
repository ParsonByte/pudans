package cn.tolim.pudans.communal.mq.consumer;

import cn.tolim.pudans.communal.mq.message.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSubscriptionTests {

    @Test
    void wildcardAcceptsAnyTagOnMatchingTopic() {
        MessageSubscription subscription = MessageSubscription.of("order", message -> {
        });

        assertThat(subscription.accepts(Message.of("order", "x").withTag("created"))).isTrue();
        assertThat(subscription.accepts(Message.of("order", "x").withTag(null))).isTrue();
        assertThat(subscription.accepts(Message.of("payment", "x"))).isFalse();
    }

    @Test
    void tagFilterSupportsAlternativeSeparator() {
        MessageSubscription subscription = new MessageSubscription(
                "order", "created || paid", "order-consumer", message -> {
        });

        assertThat(subscription.accepts(Message.of("order", "x").withTag("paid"))).isTrue();
        assertThat(subscription.accepts(Message.of("order", "x").withTag("cancelled"))).isFalse();
        assertThat(subscription.accepts(Message.of("order", "x"))).isFalse();
    }

    @Test
    void defaultsAreApplied() {
        MessageSubscription subscription = new MessageSubscription("order", null, null, message -> {
        });

        assertThat(subscription.tagFilter()).isEqualTo(MessageSubscription.ALL_TAGS);
        assertThat(subscription.consumerGroup()).isEqualTo(MessageSubscription.DEFAULT_CONSUMER_GROUP);
    }
}