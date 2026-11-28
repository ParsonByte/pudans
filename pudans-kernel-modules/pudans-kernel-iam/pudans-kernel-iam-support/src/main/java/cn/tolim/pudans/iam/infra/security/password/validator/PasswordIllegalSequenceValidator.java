package cn.tolim.pudans.iam.infra.security.password.validator;

import cn.tolim.pudans.iam.infra.security.password.PasswordValidator;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordIllegalSequenceInvalidException;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordInvalidException;

public final class PasswordIllegalSequenceValidator implements PasswordValidator {

    private static final int SEQUENCE_LENGTH = 5;
    private static final String[][] SEQUENCES = {
            {"abcdefghijklmnopqrstuvwxyz", "ABCDEFGHIJKLMNOPQRSTUVWXYZ"},
            {"0123456789"},
            {"`1234567890-=", "~!@#$%^&*()_+"},
            {"qwertyuiop[]\\", "QWERTYUIOP{}|"},
            {"asdfghjkl;'", "ASDFGHJKL:\""},
            {"zxcvbnm,./", "ZXCVBNM<>?"}};

    private final Boolean enabled;

    public PasswordIllegalSequenceValidator(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void validate(String password) throws PasswordInvalidException {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        String value = password == null ? "" : password;
        for (String[] sequence : SEQUENCES) {
            if (containsSequence(value, sequence)) {
                throw new PasswordIllegalSequenceInvalidException("密码包含非法序列");
            }
            if (containsSequence(value, reverse(sequence))) {
                throw new PasswordIllegalSequenceInvalidException("密码包含非法序列");
            }
        }
    }

    private boolean containsSequence(String password, String[] sequence) {
        int sequenceSize = sequence[0].length();
        if (password.length() < SEQUENCE_LENGTH || sequenceSize < SEQUENCE_LENGTH) {
            return false;
        }
        for (int passwordStart = 0; passwordStart <= password.length()
                - SEQUENCE_LENGTH; passwordStart++) {
            for (int sequenceStart = 0; sequenceStart <= sequenceSize
                    - SEQUENCE_LENGTH; sequenceStart++) {
                boolean matched = true;
                for (int offset = 0; offset < SEQUENCE_LENGTH; offset++) {
                    char value = password.charAt(passwordStart + offset);
                    if (!matchesAny(sequence, sequenceStart + offset, value)) {
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAny(String[] sequence, int position, char value) {
        for (String variant : sequence) {
            char expected = variant.charAt(position);
            if (expected == value
                    || Character.toLowerCase(expected) == Character.toLowerCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String[] reverse(String[] sequence) {
        String[] reversed = new String[sequence.length];
        for (int index = 0; index < sequence.length; index++) {
            reversed[index] = new StringBuilder(sequence[index]).reverse().toString();
        }
        return reversed;
    }
}
