package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.entity.NotificationProviderEntity;
import cn.tolim.pudans.notification.entity.NotificationSendRecordEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.exception.NotificationProviderNotAvailableException;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import cn.tolim.pudans.notification.repository.NotificationProviderRepository;
import cn.tolim.pudans.notification.security.NotificationSecretCipher;
import cn.tolim.pudans.notification.sender.NotificationProviderConfig;
import cn.tolim.pudans.notification.sender.NotificationSender;
import cn.tolim.pudans.notification.sender.NotificationSenderRegistry;
import cn.tolim.pudans.notification.support.NotificationJson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Chooses the provider and sender for a notification.
@Component
public class NotificationProviderSelector {

    private final NotificationProviderRepository providerRepository;
    private final NotificationSenderRegistry senderRegistry;
    private final NotificationSecretCipher secretCipher;
    private final NotificationProviderConfigValidator configValidator;

    public NotificationProviderSelector(NotificationProviderRepository providerRepository,
                                        NotificationSenderRegistry senderRegistry,
                                        NotificationSecretCipher secretCipher,
                                        NotificationProviderConfigValidator configValidator) {
        this.providerRepository = providerRepository;
        this.senderRegistry = senderRegistry;
        this.secretCipher = secretCipher;
        this.configValidator = configValidator;
    }

    public Selection select(NotificationChannel channel, String providerCode) {
        if (channel == null) {
            throw new NotificationValidationException("Notification channel is required");
        }
        if (providerCode != null && !providerCode.isBlank()) {
            return selectByCode(channel, providerCode);
        }
        return selectByPriority(channel);
    }

    public Selection selectForRecord(NotificationSendRecordEntity record) {
        if (record.getProviderCode() != null && !record.getProviderCode().isBlank()) {
            Optional<NotificationProviderEntity> provider = providerRepository
                    .findByCodeAndEnabledTrue(record.getProviderCode());
            if (provider.isPresent()) {
                NotificationProviderEntity entity = provider.get();
                if (entity.getChannel().equals(record.getChannel())) {
                    return resolve(entity);
                }
            }
        }
        return selectByPriority(record.getChannel());
    }

    public Selection resolve(NotificationProviderEntity entity) {
        if (entity.getProvider() == null) {
            throw new NotificationValidationException(
                    "Provider " + entity.getCode() + " has no provider type");
        }
        NotificationSender sender = senderRegistry.get(entity.getProvider());
        NotificationProviderConfig config = readConfiguration(entity);
        return new Selection(entity, config, sender);
    }

    public NotificationProviderConfig readConfiguration(NotificationProviderEntity entity) {
        String stored = entity.getConfiguration();
        if (stored == null || stored.isBlank()) {
            return new NotificationProviderConfig(Map.of());
        }
        String json = secretCipher.isEncrypted(stored) ? secretCipher.decrypt(stored) : stored;
        return new NotificationProviderConfig(NotificationJson.readMap(json));
    }

    private Selection selectByCode(NotificationChannel channel, String providerCode) {
        NotificationProviderEntity entity = providerRepository.findByCode(providerCode)
                .orElseThrow(() -> new NotificationProviderNotAvailableException(
                        "Notification provider not found: " + providerCode));
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new NotificationProviderNotAvailableException(
                    "Notification provider is disabled: " + providerCode);
        }
        if (channel != null && !entity.getChannel().equals(channel)) {
            throw new NotificationValidationException(
                    "Provider " + providerCode + " serves " + entity.getChannel()
                            + " but the request uses " + channel);
        }
        Selection selection = resolve(entity);
        configValidator.validate(entity.getChannel(), entity.getProvider(),
                selection.config().asMap());
        return selection;
    }

    private Selection selectByPriority(NotificationChannel channel) {
        List<NotificationProviderEntity> candidates = providerRepository
                .findByChannelAndEnabledTrueOrderByPriorityAsc(channel);
        List<String> skipped = new ArrayList<>();
        for (NotificationProviderEntity entity : candidates) {
            if (!senderRegistry.supports(entity.getProvider())) {
                skipped.add(entity.getCode() + ": no sender for " + entity.getProvider());
                continue;
            }
            try {
                Selection selection = resolve(entity);
                configValidator.validate(selection.provider().getChannel(),
                        selection.provider().getProvider(), selection.config().asMap());
                return selection;
            } catch (RuntimeException exception) {
                skipped.add(entity.getCode() + ": " + exception.getMessage());
            }
        }
        String detail = skipped.isEmpty() ? "no enabled provider configured"
                : "candidates rejected: " + String.join("; ", skipped);
        throw new NotificationProviderNotAvailableException(
                "No usable notification provider for channel " + channel + " (" + detail + ")");
    }

    // Resolved provider plus the sender selected for it.
    public record Selection(NotificationProviderEntity provider, NotificationProviderConfig config,
                            NotificationSender sender) {

        public ProviderType providerType() {
            return provider.getProvider();
        }
    }
}