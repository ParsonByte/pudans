package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.core.webhook.OutboundRequestValidator;
import cn.tolim.pudans.core.webhook.ValidatingAddressResolverGroup;
import cn.tolim.pudans.infra.properties.LiveProperties;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class LiveWebClientConfiguration {

    // 构建直播解析专用客户端，限制体积、禁止重定向并在连接期校验地址
    @Bean("liveWebClient")
    WebClient liveWebClient(LiveProperties properties, OutboundRequestValidator validator) {
        var resolve = properties.getResolve();
        var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(resolve.getConnectTimeout().toMillis()))
                .responseTimeout(resolve.getResponseTimeout())
                .resolver(new ValidatingAddressResolverGroup(validator))
                .followRedirect(false);
        var strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs()
                        .maxInMemorySize(Math.toIntExact(resolve.getMaxHtmlBytes().toBytes())))
                .build();
        var builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.USER_AGENT, resolve.getUserAgent())
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        if (StringUtils.hasText(resolve.getCookie())) {
            builder.defaultHeader(HttpHeaders.COOKIE, resolve.getCookie());
        }
        return builder.build();
    }
}
