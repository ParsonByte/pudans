package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.infra.properties.LiveProperties;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class LiveWebClientConfiguration {

    @Bean("liveWebClient")
    WebClient liveWebClient(LiveProperties properties) {
        var resolve = properties.getResolve();
        var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(resolve.getConnectTimeout().toMillis()))
                .responseTimeout(resolve.getResponseTimeout())
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
