package cn.tolim.pudans.iam.core.configuration;

import cn.tolim.pudans.iam.communal.constant.ConfigBeanNameConstants;
import cn.tolim.pudans.iam.communal.repository.account.UserHistoryPasswordRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.core.security.password.manager.DefaultPasswordPolicyManager;
import cn.tolim.pudans.iam.infra.security.password.PasswordPolicyManager;
import cn.tolim.pudans.iam.infra.security.password.generator.DefaultPasswordGenerator;
import cn.tolim.pudans.iam.infra.security.password.weak.DefaultPasswordWeakLibImpl;
import cn.tolim.pudans.iam.infra.security.password.weak.PasswordWeakLib;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;


@Configuration
public class IamPasswordConfiguration {

    // Password policy manager
    // Parameter userRepository: UserRepository UserRepository
    // Parameter userHistoryPasswordRepository: UserHistoryPasswordRepository User password history repository
    // Parameter settingRepository: SettingRepository SettingRepository
    // Parameter passwordWeakLib: PasswordWeakLib PasswordWeakLib
    // Parameter passwordEncoder: PasswordEncoder PasswordEncoder
    // Returns: DefaultPasswordPolicyManager
    @Bean(ConfigBeanNameConstants.DEFAULT_PASSWORD_POLICY_MANAGER)
    @RefreshScope
    public PasswordPolicyManager passwordPolicyManager(UserRepository userRepository,
                                                       UserHistoryPasswordRepository userHistoryPasswordRepository,
                                                       SettingRepository settingRepository,
                                                       PasswordWeakLib passwordWeakLib,
                                                       PasswordEncoder passwordEncoder) {
        return new DefaultPasswordPolicyManager(userRepository, userHistoryPasswordRepository,
                settingRepository, passwordWeakLib, passwordEncoder);
    }

    // Default password generation
    // Returns: DefaultPasswordGenerator
    @Bean
    public DefaultPasswordGenerator defaultPasswordGenerator() {
        return new DefaultPasswordGenerator();
    }

    // Weak password dictionary
    // Returns: PasswordWeakLib
    @Bean
    public PasswordWeakLib passwordWeakLib() {
        return new DefaultPasswordWeakLibImpl();
    }

}
