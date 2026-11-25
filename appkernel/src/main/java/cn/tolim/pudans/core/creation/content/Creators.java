package cn.tolim.pudans.core.creation.content;

import cn.tolim.pudans.contract.creation.dto.CreatorFollowResult;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.InteractionRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class Creators {

    private final InteractionRepository interactions;

    private Clock clock = Clock.systemUTC();

    public Mono<CreatorFollowResult> follow(RequestIdentity identity, String creatorId) {
        if (!StringUtils.hasText(creatorId)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "creator-required", "创作者不能为空。"));
        }
        var creator = creatorId.trim();
        if (creator.equals(identity.ownerId())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "creator-follow-self", "不能关注自己。"));
        }
        return interactions
                .follow(identity.ownerId(), creator, identity.workspaceId(), clock.instant())
                .then(interactions.followerCount(creator))
                .map(count -> new CreatorFollowResult(true, count));
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
