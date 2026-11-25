package cn.tolim.pudans.core.creation.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.InteractionRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CreatorsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    InteractionRepository interactions;

    private Creators creators;

    @BeforeEach
    void setUp() {
        creators = new Creators(interactions);
        creators.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void followsACreatorInTheCurrentWorkspaceAndReturnsTheRealCount() {
        when(interactions.follow("alice", "bob", "workspace-a", NOW)).thenReturn(Mono.empty());
        when(interactions.followerCount("bob")).thenReturn(Mono.just(12L));

        StepVerifier.create(creators.follow(ALICE, " bob "))
                .assertNext(result -> {
                    assertThat(result.following()).isTrue();
                    assertThat(result.followerCount()).isEqualTo(12);
                })
                .verifyComplete();

        verify(interactions).follow("alice", "bob", "workspace-a", NOW);
    }

    @Test
    void rejectsFollowingTheCurrentIdentity() {
        StepVerifier.create(creators.follow(ALICE, "alice"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(400);
                            assertThat(api.getCode()).isEqualTo("creator-follow-self");
                        }))
                .verify();

        verifyNoInteractions(interactions);
    }
}
