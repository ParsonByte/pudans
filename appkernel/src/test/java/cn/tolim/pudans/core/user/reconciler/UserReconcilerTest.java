package cn.tolim.pudans.core.user.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.InternalReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.controller.Reconciler.Request;
import cn.tolim.pudans.contract.security.device.DeviceService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.service.AnnouncementStateService;
import cn.tolim.pudans.core.user.service.AvatarStorageService;
import cn.tolim.pudans.core.user.service.HashIdService;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UserReconcilerTest {

    private static final String USERNAME = "alice";

    @Mock
    InternalReactiveExtensionClient client;

    @Mock
    DeviceService deviceService;

    @Mock
    DomainEventPublisher domainEventPublisher;

    @Mock
    AvatarStorageService avatarStorageService;

    @Mock
    HashIdService hashIdService;

    @Mock
    AnnouncementStateService announcementStateService;

    private UserReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new UserReconciler(
                client,
                deviceService,
                domainEventPublisher,
                avatarStorageService,
                hashIdService,
                announcementStateService);
    }

    private static User user(String name) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(name);
        user.setMetadata(metadata);
        return user;
    }

    @Test
    void reconcileAddsTheProtectionFinalizerThroughTheInternalUpdatePath() {
        var user = user(USERNAME);
        when(client.fetch(User.class, USERNAME)).thenReturn(Mono.just(user));
        when(client.updateInternal(any(User.class))).thenReturn(Mono.just(user));

        reconciler.reconcileReactive(new Request(USERNAME)).block();

        var captor = ArgumentCaptor.forClass(User.class);
        verify(client).updateInternal(captor.capture());
        assertThat(captor.getValue().getMetadata().getFinalizers()).contains("user-protection");
        verify(client, never()).update(any(User.class));
    }

    @Test
    void reconcileSkipsTheUpdateWhenTheFinalizerIsAlreadyPresent() {
        var user = user(USERNAME);
        user.getMetadata().setFinalizers(java.util.Set.of("user-protection"));
        when(client.fetch(User.class, USERNAME)).thenReturn(Mono.just(user));

        reconciler.reconcileReactive(new Request(USERNAME)).block();

        verify(client, never()).updateInternal(any(User.class));
    }
}
