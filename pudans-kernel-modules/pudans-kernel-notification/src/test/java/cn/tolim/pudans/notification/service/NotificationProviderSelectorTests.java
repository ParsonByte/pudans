package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.entity.NotificationProviderEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.exception.NotificationProviderNotAvailableException;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import cn.tolim.pudans.notification.repository.NotificationProviderRepository;
import cn.tolim.pudans.notification.security.NotificationSecretCipher;
import cn.tolim.pudans.notification.sender.NotificationMessage;
import cn.tolim.pudans.notification.sender.NotificationProviderConfig;
import cn.tolim.pudans.notification.sender.NotificationSender;
import cn.tolim.pudans.notification.sender.NotificationSenderRegistry;
import cn.tolim.pudans.notification.sender.SendResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationProviderSelectorTests {

    private final NotificationProviderRepository repository =
            mock(NotificationProviderRepository.class);
    private final NotificationSecretCipher cipher = mock(NotificationSecretCipher.class);
    private final NotificationSender smtpSender = new StubSender(NotificationChannel.EMAIL,
            ProviderType.SMTP);
    private final NotificationSender aliyunSender = new StubSender(NotificationChannel.SMS,
            ProviderType.ALIYUN_SMS);
    private final NotificationSenderRegistry registry =
            new NotificationSenderRegistry(List.of(smtpSender, aliyunSender));
    private final NotificationProviderSelector selector = new NotificationProviderSelector(
            repository, registry, cipher, new NotificationProviderConfigValidator());

    {
        when(cipher.isEncrypted(any())).thenReturn(false);
    }

    @Test
    void selectsHighestPriorityProviderWithARegisteredSender() {
        NotificationProviderEntity unsupported = provider("tencent", NotificationChannel.SMS,
                ProviderType.TENCENT_SMS, 1,
                "{\"secretId\":\"id\",\"secretKey\":\"key\",\"sdkAppId\":\"1\",\"signName\":\"P\"}");
        NotificationProviderEntity aliyun = provider("aliyun", NotificationChannel.SMS,
                ProviderType.ALIYUN_SMS, 5,
                "{\"accessKeyId\":\"ak\",\"accessKeySecret\":\"sk\",\"signName\":\"Pudans\"}");
        when(repository.findByChannelAndEnabledTrueOrderByPriorityAsc(NotificationChannel.SMS))
                .thenReturn(List.of(unsupported, aliyun));

        NotificationProviderSelector.Selection selection = selector.select(
                NotificationChannel.SMS, null);

        assertThat(selection.provider().getCode()).isEqualTo("aliyun");
        assertThat(selection.sender()).isSameAs(aliyunSender);
        assertThat(selection.config().require("signName")).isEqualTo("Pudans");
    }

    @Test
    void skipsProvidersWithInvalidConfigurationAndFallsBackToTheNextCandidate() {
        NotificationProviderEntity invalid = provider("broken", NotificationChannel.SMS,
                ProviderType.ALIYUN_SMS, 1, "{\"accessKeyId\":\"ak\"}");
        NotificationProviderEntity valid = provider("aliyun", NotificationChannel.SMS,
                ProviderType.ALIYUN_SMS, 2,
                "{\"accessKeyId\":\"ak\",\"accessKeySecret\":\"sk\",\"signName\":\"Pudans\"}");
        when(repository.findByChannelAndEnabledTrueOrderByPriorityAsc(NotificationChannel.SMS))
                .thenReturn(List.of(invalid, valid));

        NotificationProviderSelector.Selection selection = selector.select(
                NotificationChannel.SMS, null);

        assertThat(selection.provider().getCode()).isEqualTo("aliyun");
    }

    @Test
    void honoursExplicitProviderCode() {
        NotificationProviderEntity aliyun = provider("aliyun", NotificationChannel.SMS,
                ProviderType.ALIYUN_SMS, 9,
                "{\"accessKeyId\":\"ak\",\"accessKeySecret\":\"sk\",\"signName\":\"Pudans\"}");
        when(repository.findByCode("aliyun")).thenReturn(Optional.of(aliyun));

        NotificationProviderSelector.Selection selection = selector.select(
                NotificationChannel.SMS, "aliyun");

        assertThat(selection.provider().getCode()).isEqualTo("aliyun");
    }

    @Test
    void rejectsExplicitProviderThatServesAnotherChannel() {
        NotificationProviderEntity smtp = provider("smtp", NotificationChannel.EMAIL,
                ProviderType.SMTP, 1, "{\"host\":\"smtp.example.com\",\"from\":\"a@b.com\"}");
        when(repository.findByCode("smtp")).thenReturn(Optional.of(smtp));

        assertThatThrownBy(() -> selector.select(NotificationChannel.SMS, "smtp"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("serves EMAIL");
    }

    @Test
    void rejectsDisabledExplicitProvider() {
        NotificationProviderEntity aliyun = provider("aliyun", NotificationChannel.SMS,
                ProviderType.ALIYUN_SMS, 1,
                "{\"accessKeyId\":\"ak\",\"accessKeySecret\":\"sk\",\"signName\":\"Pudans\"}");
        aliyun.setEnabled(false);
        when(repository.findByCode("aliyun")).thenReturn(Optional.of(aliyun));

        assertThatThrownBy(() -> selector.select(NotificationChannel.SMS, "aliyun"))
                .isInstanceOf(NotificationProviderNotAvailableException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void failsWhenNoEnabledProviderExists() {
        when(repository.findByChannelAndEnabledTrueOrderByPriorityAsc(NotificationChannel.FEISHU))
                .thenReturn(List.of());

        assertThatThrownBy(() -> selector.select(NotificationChannel.FEISHU, null))
                .isInstanceOf(NotificationProviderNotAvailableException.class)
                .hasMessageContaining("No usable notification provider");
    }

    private NotificationProviderEntity provider(String code, NotificationChannel channel,
                                                ProviderType type, int priority,
                                                String configuration) {
        NotificationProviderEntity entity = new NotificationProviderEntity();
        entity.setCode(code);
        entity.setName(code);
        entity.setChannel(channel);
        entity.setProvider(type);
        entity.setPriority(priority);
        entity.setEnabled(true);
        entity.setConfiguration(configuration);
        return entity;
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