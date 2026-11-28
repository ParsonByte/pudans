package cn.tolim.pudans.notification.sender;

import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.exception.NotificationProviderNotAvailableException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Resolves the sender implementation for a provider type.
@Component
public class NotificationSenderRegistry {

    private final Map<ProviderType, NotificationSender> senders;

    public NotificationSenderRegistry(List<NotificationSender> senderList) {
        Map<ProviderType, NotificationSender> registered = new EnumMap<>(ProviderType.class);
        if (senderList != null) {
            for (NotificationSender sender : senderList) {
                for (ProviderType providerType : sender.providerTypes()) {
                    NotificationSender previous = registered.putIfAbsent(providerType, sender);
                    if (previous != null) {
                        throw new IllegalArgumentException(
                                "Duplicate notification sender for provider " + providerType);
                    }
                }
            }
        }
        this.senders = Collections.unmodifiableMap(registered);
    }

    public Optional<NotificationSender> find(ProviderType providerType) {
        return Optional.ofNullable(senders.get(providerType));
    }

    public boolean supports(ProviderType providerType) {
        return senders.containsKey(providerType);
    }

    public NotificationSender get(ProviderType providerType) {
        NotificationSender sender = senders.get(providerType);
        if (sender == null) {
            throw new NotificationProviderNotAvailableException(
                    "No sender registered for provider " + providerType);
        }
        return sender;
    }

    public Map<ProviderType, NotificationSender> senders() {
        return senders;
    }
}