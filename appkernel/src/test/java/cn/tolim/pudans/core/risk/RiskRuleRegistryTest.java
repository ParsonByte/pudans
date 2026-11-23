package cn.tolim.pudans.core.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.Metadata;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskRuleRegistryTest {

    @Mock
    ExtensionClient extensionClient;

    @Test
    void everyDefaultSceneIsExposedForTheBanCheck() {
        var registry = new RiskRuleRegistry(extensionClient);

        assertThat(registry.enabledScenes())
                .contains(
                        RiskScene.REQUEST_FLOOD,
                        RiskScene.LOGIN_ATTEMPT,
                        RiskScene.LOGIN_FAILURE,
                        RiskScene.AUTH_FAILURE,
                        RiskScene.SERVER_ERROR,
                        RiskScene.TOTP_ATTEMPT,
                        RiskScene.SUDO_SEND,
                        RiskScene.SUDO_CONFIRM);
    }

    @Test
    void refreshKeepsConfiguredRuleLabelsForAnalytics() {
        var rule = new RiskRule();
        var metadata = new Metadata();
        metadata.setName(RiskScene.LOGIN_FAILURE);
        metadata.setAnnotations(Map.of(
                "rbac.authorization.pudans.tolim.cn/display-name", "连续登录失败"));
        rule.setMetadata(metadata);
        var spec = new RiskRule.Spec();
        spec.setScene(RiskScene.LOGIN_FAILURE);
        rule.setSpec(spec);
        when(extensionClient.listAll(any(), any(), any())).thenReturn(List.of(rule));
        var registry = new RiskRuleRegistry(extensionClient);

        registry.refresh();

        assertThat(registry.label(RiskScene.LOGIN_FAILURE)).isEqualTo("连续登录失败");
        assertThat(registry.rules()).containsKey(RiskScene.LOGIN_FAILURE);
    }
}
