package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParameterSettingModelValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validGroupModelsHaveNoViolations() {
        assertThat(validator.validate(validSite())).isEmpty();
        assertThat(validator.validate(validSession())).isEmpty();
        assertThat(validator.validate(validCaptcha())).isEmpty();
        var none = validCaptcha();
        none.setCaptchaProvider("none");
        assertThat(validator.validate(none)).isEmpty();
        assertThat(validator.validate(validMail())).isEmpty();
        assertThat(validator.validate(validServer())).isEmpty();
    }

    @Test
    void siteRejectsInvalidUrlsAndBlankName() {
        var site = validSite();
        site.setSiteUrl("ftp://example.com");
        site.setFavicon("not-a-url");
        site.setSiteName(" ");
        assertThat(validator.validate(site)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("siteUrl", "favicon", "siteName");
    }

    @Test
    void sessionRejectsEscapingPathsAndOutOfRangeSizes() {
        var session = validSession();
        session.setAvatarStoragePath("../outside");
        session.setAvatarMaxSize(0);
        session.setAvatarDimension(4096);
        assertThat(validator.validate(session)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("avatarStoragePath", "avatarMaxSize", "avatarDimension");
    }

    @Test
    void captchaRejectsUnknownProviderModeAndLength() {
        var captcha = validCaptcha();
        captcha.setCaptchaProvider("bad provider!");
        captcha.setCaptchaMode("IMAGE");
        captcha.setCaptchaLength(2);
        assertThat(validator.validate(captcha)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("captchaProvider", "captchaMode", "captchaLength");
    }

    @Test
    void mailRejectsInvalidAddressPortAndTtl() {
        var mail = validMail();
        mail.setMailFromAddress("not-an-email");
        mail.setSmtpPort(70000);
        mail.setSmtpConnectionTtlSeconds(-1);
        assertThat(validator.validate(mail)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("mailFromAddress", "smtpPort", "smtpConnectionTtlSeconds");
    }

    @Test
    void serverRejectsAbsoluteTempPathAndShortTtls() {
        var server = validServer();
        server.setTempPath("/absolute");
        server.setAccessTokenTtlSeconds(10);
        server.setRefreshTokenTtlSeconds(10);
        assertThat(validator.validate(server)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("tempPath", "accessTokenTtlSeconds", "refreshTokenTtlSeconds");
    }

    private static ParameterSetting.Site validSite() {
        var site = new ParameterSetting.Site();
        site.setSiteName("Pudans");
        site.setSiteUrl("");
        site.setLogoDark("");
        site.setLogoLight("");
        site.setFavicon("");
        site.setIconMedium("");
        site.setIconLarge("");
        return site;
    }

    private static ParameterSetting.Session validSession() {
        var session = new ParameterSetting.Session();
        session.setDefaultUserGroup("normal-users");
        session.setAvatarStoragePath("avatars");
        session.setAvatarMaxSize(2097152);
        session.setAvatarDimension(256);
        return session;
    }

    private static ParameterSetting.Captcha validCaptcha() {
        var captcha = new ParameterSetting.Captcha();
        captcha.setCaptchaProvider("image");
        captcha.setCaptchaMode("TEXT");
        captcha.setCaptchaLength(5);
        return captcha;
    }

    private static ParameterSetting.Mail validMail() {
        var mail = new ParameterSetting.Mail();
        mail.setMailFromAddress("no-reply@example.com");
        mail.setSmtpPort(587);
        mail.setSmtpConnectionTtlSeconds(300);
        return mail;
    }

    private static ParameterSetting.Server validServer() {
        var server = new ParameterSetting.Server();
        server.setTempPath("tmp");
        server.setAccessTokenTtlSeconds(1800);
        server.setRefreshTokenTtlSeconds(2592000);
        server.setGcCron("0 0 3 * * *");
        return server;
    }
}
