package cn.tolim.pudans.iam.synchronizer.provider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
public class LegacyIdentitySourceProviderAutoConfiguration {

    // Register the DingTalk provider definition alongside the legacy entity enum value
    @Bean
    @ConditionalOnMissingBean
    public DingTalkIdentitySourceProvider dingTalkIdentitySourceProvider() {
        return new DingTalkIdentitySourceProvider();
    }

    // Register the Feishu provider definition alongside the legacy entity enum value
    @Bean
    @ConditionalOnMissingBean
    public FeiShuIdentitySourceProvider feiShuIdentitySourceProvider() {
        return new FeiShuIdentitySourceProvider();
    }
}