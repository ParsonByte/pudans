package cn.tolim.pudans.infra.feishu;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.contract.risk.dto.RiskAlert;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuAlertNotifier {

    private final FeishuClient feishuClient;

    private final FeishuProperties properties;

    public Mono<Void> send(RiskAlert alert) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getAlertChatId())) {
            return Mono.empty();
        }
        return feishuClient
                .sendAlertCard(properties.getAlertChatId(), JsonUtils.objectToJson(card(alert)))
                .timeout(Duration.ofSeconds(5))
                .doOnError(error -> log.warn("无法向飞书发送风险告警", error))
                .onErrorResume(error -> Mono.empty());
    }

    private Map<String, Object> card(RiskAlert alert) {
        var card = new LinkedHashMap<String, Object>();
        card.put("config", Map.of("wide_screen_mode", true));
        card.put(
                "header",
                Map.of(
                        "template", "red",
                        "title", Map.of("tag", "plain_text", "content", "pudans risk alert")));
        card.put(
                "elements",
                List.of(Map.of(
                        "tag", "div",
                        "text",
                        Map.of(
                                "tag",
                                "lark_md",
                                "content",
                                "**Scene**: " + alert.scene() + "\n**Subject**: " + alert.subject() + "\n**Count**: "
                                        + alert.count() + "\n**Path**: " + alert.path() + "\n**Time**: "
                                        + alert.occurredAt()))));
        return card;
    }
}
