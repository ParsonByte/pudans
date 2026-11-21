package cn.tolim.pudans.security.authentication.impl;

import cn.tolim.pudans.infra.setting.SecurityParameters;
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

    private final SecurityParameters securitySettings;

    private final PasswordEncoder argon2;

    private final PasswordEncoder bcrypt;

    private final DelegatingPasswordEncoder delegating;

    // 预先注册两种算法，由配置决定新密码的哈希方式
    public PreferredPasswordEncoder(SecurityParameters securitySettings) {
        this.securitySettings = securitySettings;
        this.argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        this.bcrypt = new BCryptPasswordEncoder();
        var encoders = new HashMap<String, PasswordEncoder>();
        encoders.put(ARGON2_ENCODING_ID, argon2);
        encoders.put(BCRYPT_ENCODING_ID, bcrypt);
        this.delegating = new DelegatingPasswordEncoder(ARGON2_ENCODING_ID, encoders);
    }

    // 按配置选择算法，并写入前缀供校验时识别
    @Override
    public String encode(CharSequence rawPassword) {
        if (securitySettings.isArgon2PasswordStorageEnabled()) {
            return "{" + ARGON2_ENCODING_ID + "}" + argon2.encode(rawPassword);
        }
        return "{" + BCRYPT_ENCODING_ID + "}" + bcrypt.encode(rawPassword);
    }

    // 依据哈希前缀委派算法，兼容历史密码数据
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encodedPassword != null && delegating.matches(rawPassword, encodedPassword);
    }

    // 启用 Argon2 后，登录成功时把旧 BCrypt 哈希升级为 Argon2
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null
                && securitySettings.isArgon2PasswordStorageEnabled()
                && !encodedPassword.startsWith("{" + ARGON2_ENCODING_ID + "}");
    }
}
