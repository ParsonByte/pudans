package cn.tolim.pudans.notification.sender;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.exception.NotificationProviderNotAvailableException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSenderRegistryTests {

    @Test
    void resolvesSenderByProviderType() {
        NotificationSender smtp = new StubSender(NotificationChannel.EMAIL, ProviderType.SMTP);
        NotificationSender aliyun = new StubSender(NotificationChannel.SMS,
                ProviderType.ALIYUN_SMS);

        NotificationSenderRegistry registry = new NotificationSenderRegistry(List.of(smtp, aliyun));

        assertThat(registry.find(ProviderType.SMTP)).containsSame(smtp);
        assertThat(registry.find(ProviderType.ALIYUN_SMS)).containsSame(aliyun);
        assertThat(registry.supports(ProviderType.TENCENT_SMS)).isFalse();
        assertThat(registry.get(ProviderType.SMTP)).isSameAs(smtp);
    }

    @Test
    void rejectsDuplicateSendersForTheSameProviderType() {
        NotificationSender first = new StubSender(NotificationChannel.SMS, ProviderType.ALIYUN_SMS);
        NotificationSender second = new StubSender(NotificationChannel.SMS, ProviderType.ALIYUN_SMS);

        assertThatThrownBy(() -> new NotificationSenderRegistry(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate notification sender");
    }

    @Test
    void failsWhenProviderHasNoSender() {
        NotificationSenderRegistry registry = new NotificationSenderRegistry(List.of());

        assertThatThrownBy(() -> registry.get(ProviderType.DINGTALK))
                .isInstanceOf(NotificationProviderNotAvailableException.class)
                .hasMessageContaining("No sender registered");
    }

    private record StubSender(NotificationChannel channel, Set<ProviderType> providers)
            implements NotificationSender {

        StubSender(NotificationChannel channel, ProviderType providerType) {
            this(channel, Set.of(providerType));
        }

        @Override
        public Set<ProviderType> providerTypes() {
            return providers;
        }

        @Override
        public SendResult send(NotificationMessage message, NotificationProviderConfig config) {
            return SendResult.success(null, "stub");
        }
    }
}