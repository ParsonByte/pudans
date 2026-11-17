package cn.tolim.pudans.security.authentication.impl;

import cn.tolim.pudans.infra.setting.SecurityParameterSettingService;
import java.util.HashMap;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("deprecation")
public class PreferredPasswordEncoder implements PasswordEncoder {

    public static final String ARGON2_ENCODING_ID = "argon2@SpringSecurity_v5_8";

    public static final String BCRYPT_ENCODING_ID = "bcrypt";

    private final SecurityParameterSettingService securitySettings;

    private final PasswordEncoder argon2;

    private final PasswordEncoder bcrypt;

    private final DelegatingPasswordEncoder delegating;

    public PreferredPasswordEncoder(SecurityParameterSettingService securitySettings) {
        this.securitySettings = securitySettings;
        this.argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        this.bcrypt = new BCryptPasswordEncoder();
        var encoders = new HashMap<String, PasswordEncoder>();
        encoders.put(ARGON2_ENCODING_ID, argon2);
        encoders.put(BCRYPT_ENCODING_ID, bcrypt);
        this.delegating = new DelegatingPasswordEncoder(ARGON2_ENCODING_ID, encoders);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (securitySettings.isArgon2PasswordStorageEnabled()) {
            return "{" + ARGON2_ENCODING_ID + "}" + argon2.encode(rawPassword);
        }
        return "{" + BCRYPT_ENCODING_ID + "}" + bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encodedPassword != null && delegating.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return false;
    }
}
