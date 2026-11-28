package cn.tolim.pudans.iam.infra.security.crypto.password;

import org.springframework.security.crypto.password.PasswordEncoder;

public final class NoOpPasswordEncoder implements PasswordEncoder {

    private static final PasswordEncoder INSTANCE = new NoOpPasswordEncoder();

    private NoOpPasswordEncoder() {
    }

    public static PasswordEncoder getInstance() {
        return INSTANCE;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return rawPassword.toString();
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return rawPassword.toString().equals(encodedPassword);
    }
}
