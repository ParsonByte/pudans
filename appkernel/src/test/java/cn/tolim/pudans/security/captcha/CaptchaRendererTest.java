package cn.tolim.pudans.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import org.junit.jupiter.api.Test;

class CaptchaRendererTest {

    private final CaptchaRenderer renderer = new CaptchaRenderer();

    @Test
    void rendersATextCaptcha() {
        var captcha = new ParameterSetting.Captcha();
        captcha.setCaptchaMode("TEXT");
        captcha.setCaptchaLength(5);
        captcha.setCaptchaUseNoise(true);
        captcha.setCaptchaUseWaveLine(true);
        captcha.setCaptchaUseHollowLine(true);

        var rendered = renderer.render(captcha);

        assertThat(rendered.answer()).hasSize(5);
        assertThat(rendered.imageDataUrl()).startsWith("data:image/png;base64,");
        assertThat(rendered.imageDataUrl().length()).isGreaterThan(100);
    }

    @Test
    void rendersAMathCaptcha() {
        var captcha = new ParameterSetting.Captcha();
        captcha.setCaptchaMode("MATH");
        captcha.setCaptchaInterferenceText(true);
        captcha.setCaptchaInterferenceDots(true);
        captcha.setCaptchaUseSineLine(true);

        var rendered = renderer.render(captcha);

        assertThat(rendered.answer()).matches("\\d+");
        assertThat(rendered.imageDataUrl()).startsWith("data:image/png;base64,");
    }
}
