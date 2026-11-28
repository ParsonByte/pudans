package cn.tolim.pudans.notification.sender;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;

import java.util.Set;

// SPI for an outbound notification provider.
public interface NotificationSender {

    NotificationChannel channel();

    Set<ProviderType> providerTypes();

    SendResult send(NotificationMessage message, NotificationProviderConfig config);
}