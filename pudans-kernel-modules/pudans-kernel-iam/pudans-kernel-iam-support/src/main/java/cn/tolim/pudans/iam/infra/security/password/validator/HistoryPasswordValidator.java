package cn.tolim.pudans.iam.infra.security.password.validator;

import cn.tolim.pudans.iam.infra.security.password.PasswordValidator;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordHistoryInvalidException;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordInvalidException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

public final class HistoryPasswordValidator implements PasswordValidator {

    private final Boolean enabled;
    private final List<String> historyPasswords;
    private final PasswordEncoder passwordEncoder;

    public HistoryPasswordValidator(Boolean enabled) {
        this.enabled = enabled;
        this.historyPasswords = List.of();
        this.passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    public HistoryPasswordValidator(Boolean enabled, List<String> historyPasswords,
                                    PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.historyPasswords = historyPasswords;
        this.passwordEncoder = passwordEncoder;
    }

    public HistoryPasswordValidator(List<String> historyPasswords,
                                    PasswordEncoder passwordEncoder) {
        this(Boolean.TRUE, historyPasswords, passwordEncoder);
    }

    @Override
    public void validate(String password) throws PasswordInvalidException {
        if (!Boolean.TRUE.equals(enabled) || historyPasswords == null) {
            return;
        }
        for (String historyPassword : historyPasswords) {
            if (passwordEncoder.matches(password, historyPassword)) {
                throw new PasswordHistoryInvalidException("密码与历史密码相同");
            }
        }
    }
}
