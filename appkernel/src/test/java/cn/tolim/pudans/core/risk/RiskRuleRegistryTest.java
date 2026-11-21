package cn.tolim.pudans.core.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.extension.ExtensionClient;

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
}
