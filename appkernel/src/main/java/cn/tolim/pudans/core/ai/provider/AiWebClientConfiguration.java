package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.core.infra.properties.AiProperties;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration(proxyBeanMethods = false)
public class AiWebClientConfiguration {

    public static final String BEAN_NAME = "aiWebClient";

    @Bean(BEAN_NAME)
    public WebClient aiWebClient(AiProperties properties) {
        var config = properties.getWebClient();
        var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(config.getConnectTimeout().toMillis()))
                .responseTimeout(config.getResponseTimeout());
        var strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs()
                        .maxInMemorySize(Math.toIntExact(config.getMaxInMemorySize().toBytes())))
                .build();
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
