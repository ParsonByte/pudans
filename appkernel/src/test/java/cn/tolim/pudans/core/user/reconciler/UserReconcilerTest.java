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
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.controller.Reconciler.Request;
import cn.tolim.pudans.security.contract.device.DeviceSessions;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.AnnouncementReads;
import cn.tolim.pudans.core.user.AvatarStorage;
import cn.tolim.pudans.core.user.HashIds;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UserReconcilerTest {

    private static final String USERNAME = "alice";

    @Mock
    InternalReactiveExtensionClient client;

    @Mock
    DeviceSessions deviceSessions;

    @Mock
    DomainEventPublisher domainEventPublisher;

    @Mock
    AvatarStorage avatarStorage;

    @Mock
    HashIds hashIds;

    @Mock
    AnnouncementReads announcementReads;

    private UserReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new UserReconciler(
                client,
                deviceSessions,
                domainEventPublisher,
                avatarStorage,
                hashIds,
                announcementReads);
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
