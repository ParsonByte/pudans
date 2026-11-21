package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.contract.core.extension.live.LivePlatform;
import cn.tolim.pudans.contract.live.LiveErrorCode;
import cn.tolim.pudans.contract.live.LiveRoomInfo;
import cn.tolim.pudans.infra.properties.LiveProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class DouyinLiveRoomResolver {

    private static final String ROOM_URL = "https://live.douyin.com/";

    private final WebClient liveWebClient;

    private final LiveProperties properties;

    private final PacePayloadExtractor extractor;

    private final DouyinRoomPayloadMapper mapper;

    public DouyinLiveRoomResolver(
            @Qualifier("liveWebClient") WebClient liveWebClient,
            LiveProperties properties,
            PacePayloadExtractor extractor,
            DouyinRoomPayloadMapper mapper) {
        this.liveWebClient = liveWebClient;
        this.properties = properties;
        this.extractor = extractor;
        this.mapper = mapper;
    }

    public Mono<LiveRoomInfo> resolve(String roomId) {
        return liveWebClient
                .get()
                .uri(ROOM_URL + roomId)
                .accept(MediaType.TEXT_HTML)
                .exchangeToMono(response -> {
                    var status = response.statusCode().value();
                    if (status == HttpStatus.NOT_FOUND.value()) {
                        return Mono.error(LiveErrorCode.LIVE_ROOM_NOT_FOUND.exception());
                    }
                    if (status == HttpStatus.FORBIDDEN.value() || status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                        return Mono.error(LiveErrorCode.LIVE_RATE_LIMITED.exception());
                    }
                    if (status >= 500) {
                        return Mono.error(LiveErrorCode.LIVE_RESOLVE_FAILED.exception());
                    }
                    if (!response.statusCode().is2xxSuccessful()) {
                        return Mono.error(LiveErrorCode.LIVE_RESOLVE_FAILED.exception());
                    }
                    return response.bodyToMono(String.class);
                })
                .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_RESOLVE_FAILED.exception()))
                .flatMap(html -> Mono.fromCallable(() -> parse(html, roomId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .timeout(properties.getResolve().getResponseTimeout().plusSeconds(5))
                .onErrorResume(error -> {
                    if (error instanceof cn.tolim.pudans.infra.exception.ApiException apiException) {
                        return Mono.error(apiException);
                    }
                    log.warn("抖音直播间解析失败：platform={}，reason={}", LivePlatform.DOUYIN, error.getMessage());
                    return Mono.error(LiveErrorCode.LIVE_RESOLVE_FAILED.exception());
                });
    }

    private LiveRoomInfo parse(String html, String roomId) {
        if (looksRateLimited(html)) {
            throw LiveErrorCode.LIVE_RATE_LIMITED.exception();
        }
        return mapper.map(extractor.extract(html), roomId);
    }

    private static boolean looksRateLimited(String html) {
        var lower = html.toLowerCase();
        return lower.contains("captcha") || lower.contains("verify-center") || lower.contains("滑块验证");
    }
}
