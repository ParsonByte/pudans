package cn.tolim.pudans.core.creation.content;

import cn.tolim.pudans.contract.creation.Subject;
import cn.tolim.pudans.contract.creation.dto.SubjectPage;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import cn.tolim.pudans.infra.r2dbc.creation.SubjectRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class Subjects {

    private static final String EMPTY_STATE = "暂无主体。";

    private final SubjectRepository subjects;

    private Clock clock = Clock.systemUTC();

    public Mono<SubjectPage> mine(
            RequestIdentity identity, String cursor, Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "subject-cursor-invalid");
        return subjects
                .listMine(identity.ownerId(), identity.workspaceId(), position, limit + 1)
                .map(fetched -> page(fetched, limit));
    }

    public Mono<SubjectPage> all(String cursor, Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "subject-cursor-invalid");
        return subjects
                .listAll(position, limit + 1)
                .map(fetched -> page(fetched, limit));
    }

    private SubjectPage page(List<Subject> fetched, int limit) {
        var hasMore = fetched.size() > limit;
        var items = hasMore ? fetched.subList(0, limit) : fetched;
        var nextCursor = hasMore && !items.isEmpty()
                ? CreationCursor.encode(items.getLast().updatedAt(), items.getLast().id())
                : null;
        return new SubjectPage(
                List.copyOf(items),
                nextCursor,
                limit,
                items.isEmpty() ? EMPTY_STATE : null,
                now());
    }

    public Mono<Subject> view(RequestIdentity identity, UUID subjectId) {
        return subjects
                .findById(subjectId)
                .filter(subject -> ContentVisibility.isPublicActive(
                                subject.status(), subject.visibility())
                        || ContentVisibility.owns(
                                subject.ownerId(), subject.workspaceId(), identity))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "subject-not-found", "主体不存在。")));
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private Instant now() {
        return clock.instant();
    }
}
