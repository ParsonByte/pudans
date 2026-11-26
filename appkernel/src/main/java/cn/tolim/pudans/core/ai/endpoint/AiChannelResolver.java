package cn.tolim.pudans.core.ai.endpoint;

import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiChannelResolver {

    private final AiChannels channels;

    private final ReactiveRequestIdentity identity;

    public Mono<AiChannels.ResolvedChannel> require(ServerRequest request) {
        var id = channelId(request);
        return identity
                .resolve(request)
                .flatMap(owner -> channels.resolve(owner.workspaceId(), id));
    }

    public Mono<AiChannels.ResolvedChannel> require(ServerRequest request, String provider) {
        return require(request).flatMap(resolved -> provider.equals(resolved.channel().getProvider())
                ? Mono.just(resolved)
                : Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-channel-provider-unsupported",
                        "该渠道的服务商不是 " + provider + "。")));
    }

    private static UUID channelId(ServerRequest request) {
        return AiEndpointSupport.uuid(
                request.pathVariable("name"), "ai-channel-id-invalid", "渠道标识不是有效 UUID。");
    }
}
