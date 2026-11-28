package cn.tolim.pudans.iam.core.configuration;

import cn.tolim.pudans.contract.notification.client.NotificationClient;
import cn.tolim.pudans.contract.notification.dto.NotificationApiResult;
import cn.tolim.pudans.contract.notification.dto.SendNotificationRequest;
import cn.tolim.pudans.contract.notification.dto.SendNotificationResponse;
import feign.Feign;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationClientDecodeTests {

    // Notification service responses use ISO-8601 local date time values
    private static final String RESPONSE_BODY = """
            {"success":true,"status":"200","message":"success","result":{
            "id":"record-1","requestId":"request-1","templateCode":null,
            "channel":"EMAIL","providerCode":"smtp-main","providerType":"SMTP",
            "recipient":"alice@example.com","subject":"Subject","status":"SUCCESS",
            "attempts":1,"maxAttempts":3,"nextRetryAt":null,
            "providerMessageId":"message-1","lastError":null,
            "sentAt":"2026-11-28T10:15:30.123456",
            "createTime":"2026-11-28T10:15:30.123456"}}
            """;

    @Test
    void registersNotificationClientBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class))
                .withUserConfiguration(IamNotificationFeignConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(NotificationClient.class));
    }

    @Test
    void decodesNotificationApiResultWithIsoDateTimes() {
        NotificationClient client = createClient(RESPONSE_BODY);

        NotificationApiResult<SendNotificationResponse> result = client.send(
                SendNotificationRequest.builder().recipient("alice@example.com").channel("EMAIL")
                        .subject("Subject").content("Content").build());

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getResult().getId()).isEqualTo("record-1");
        assertThat(result.getResult().getSentAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 28, 10, 15, 30, 123456000));
        assertThat(result.getResult().getCreateTime())
                .isEqualTo(LocalDateTime.of(2026, 11, 28, 10, 15, 30, 123456000));
    }

    // Builds a Feign client decoding from a fixed JSON body
    // Parameter body: String
    // Returns: NotificationClient
    private NotificationClient createClient(String body) {
        FeignHttpMessageConverters converters = new FeignHttpMessageConverters(
                emptyProvider(), emptyProvider());
        ObjectProvider<FeignHttpMessageConverters> provider = new ObjectProvider<>() {
            @Override
            public Stream<FeignHttpMessageConverters> stream() {
                return Stream.of(converters);
            }
        };
        return Feign.builder()
                .contract(new SpringMvcContract())
                .client((request, options) -> Response.builder().status(200).reason("OK")
                        .request(request)
                        .headers(Map.of("Content-Type", List.of("application/json")))
                        .body(body, StandardCharsets.UTF_8).build())
                .encoder(new SpringEncoder(provider))
                .decoder(new SpringDecoder(provider))
                .target(NotificationClient.class, "http://notification");
    }

    private <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public Stream<T> stream() {
                return Stream.empty();
            }
        };
    }
}