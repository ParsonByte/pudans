package cn.tolim.pudans.core.creation.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Subject;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.SubjectRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SubjectsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    SubjectRepository subjects;

    private Subjects service;

    @BeforeEach
    void setUp() {
        service = new Subjects(subjects);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listsOnlyTheCurrentOwnerAndWorkspaceWithAnEmptyState() {
        when(subjects.listMine("alice", "workspace-a", null, 21)).thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.mine(ALICE, null, null))
                .assertNext(page -> {
                    assertThat(page.items()).isEmpty();
                    assertThat(page.nextCursor()).isNull();
                    assertThat(page.emptyState()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void hidesAnotherOwnersPrivateSubject() {
        var subjectId = UUID.randomUUID();
        when(subjects.findById(subjectId)).thenReturn(Mono.just(subject(
                subjectId, "bob", "workspace-a", "active", CreationValues.VISIBILITY_PRIVATE)));

        StepVerifier.create(service.view(ALICE, subjectId))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(404);
                            assertThat(api.getCode()).isEqualTo("subject-not-found");
                        }))
                .verify();
    }

    @Test
    void exposesAPublicActiveSubject() {
        var subjectId = UUID.randomUUID();
        when(subjects.findById(subjectId)).thenReturn(Mono.just(subject(
                subjectId, "bob", "workspace-b", "active", CreationValues.VISIBILITY_PUBLIC)));

        StepVerifier.create(service.view(ALICE, subjectId))
                .assertNext(subject -> assertThat(subject.id()).isEqualTo(subjectId))
                .verifyComplete();
    }

    @Test
    void allowsAnOwnerToViewAPrivateSubject() {
        var subjectId = UUID.randomUUID();
        when(subjects.findById(subjectId)).thenReturn(Mono.just(subject(
                subjectId, "alice", "workspace-a", "active", CreationValues.VISIBILITY_PRIVATE)));

        StepVerifier.create(service.view(ALICE, subjectId))
                .assertNext(subject -> assertThat(subject.id()).isEqualTo(subjectId))
                .verifyComplete();
    }

    private static Subject subject(
            UUID id, String owner, String workspace, String status, String visibility) {
        return new Subject(
                id,
                owner,
                workspace,
                "subject",
                "description",
                "character",
                null,
                List.of(),
                Map.of(),
                status,
                visibility,
                NOW,
                NOW);
    }
}
