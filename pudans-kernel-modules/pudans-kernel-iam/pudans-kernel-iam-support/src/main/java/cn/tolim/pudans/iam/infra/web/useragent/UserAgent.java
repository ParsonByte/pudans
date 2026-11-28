package cn.tolim.pudans.iam.infra.web.useragent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.io.Serializable;

public final class UserAgent implements Serializable {
    private String browser;
    private String browserType;
    private String browserMajorVersion;
    private String deviceType;
    private String platform;
    private String platformVersion;
    private String renderingEngineMaker;

    public UserAgent(String browser, String browserMajorVersion, String deviceType,
                     String platformVersion, String renderingEngineMaker, String browserType,
                     String platform) {
        this.browser = browser;
        this.browserMajorVersion = browserMajorVersion;
        this.deviceType = deviceType;
        this.platformVersion = platformVersion;
        this.renderingEngineMaker = renderingEngineMaker;
        this.browserType = browserType;
        this.platform = platform;
    }

    public static UserAgentBuilder builder() {
        return new UserAgentBuilder();
    }

    @JsonProperty("browser")
    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    @JsonProperty("browserType")
    public String getBrowserType() {
        return browserType;
    }

    public void setBrowserType(String browserType) {
        this.browserType = browserType;
    }

    @JsonProperty("browserMajorVersion")
    public String getBrowserMajorVersion() {
        return browserMajorVersion;
    }

    public void setBrowserMajorVersion(String browserMajorVersion) {
        this.browserMajorVersion = browserMajorVersion;
    }

    @JsonProperty("deviceType")
    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    @JsonProperty("platform")
    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    @JsonProperty("platformVersion")
    public String getPlatformVersion() {
        return platformVersion;
    }

    public void setPlatformVersion(String platformVersion) {
        this.platformVersion = platformVersion;
    }

    @JsonProperty("renderingEngineMaker")
    public String getRenderingEngineMaker() {
        return renderingEngineMaker;
    }

    public void setRenderingEngineMaker(String renderingEngineMaker) {
        this.renderingEngineMaker = renderingEngineMaker;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class UserAgentBuilder {
        private String browser;
        private String browserType;
        private String browserMajorVersion;
        private String deviceType;
        private String platform;
        private String platformVersion;
        private String renderingEngineMaker;

        public UserAgentBuilder browser(String value) {
            this.browser = value;
            return this;
        }

        public UserAgentBuilder browserType(String value) {
            this.browserType = value;
            return this;
        }

        public UserAgentBuilder browserMajorVersion(String value) {
            this.browserMajorVersion = value;
            return this;
        }

        public UserAgentBuilder deviceType(String value) {
            this.deviceType = value;
            return this;
        }

        public UserAgentBuilder platform(String value) {
            this.platform = value;
            return this;
        }

        public UserAgentBuilder platformVersion(String value) {
            this.platformVersion = value;
            return this;
        }

        public UserAgentBuilder renderingEngineMaker(String value) {
            this.renderingEngineMaker = value;
            return this;
        }

        public UserAgent build() {
            return new UserAgent(browser, browserMajorVersion, deviceType, platformVersion,
                    renderingEngineMaker, browserType, platform);
        }
    }
}
