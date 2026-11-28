package cn.tolim.pudans.iam.infra.security.password.generator;

import cn.tolim.pudans.iam.infra.security.password.PasswordGenerator;

import java.security.SecureRandom;

public final class DefaultPasswordGenerator implements PasswordGenerator {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String ALL = UPPER + LOWER + DIGITS;
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generatePassword() {
        char[] password = new char[LENGTH];
        int index = 0;
        index = fill(password, index, UPPER, 2);
        index = fill(password, index, LOWER, 2);
        index = fill(password, index, DIGITS, 2);
        while (index < LENGTH) {
            password[index++] = randomChar(ALL);
        }
        for (int i = password.length - 1; i > 0; i--) {
            int next = random.nextInt(i + 1);
            char value = password[i];
            password[i] = password[next];
            password[next] = value;
        }
        return new String(password);
    }

    private int fill(char[] password, int index, String characters, int count) {
        for (int i = 0; i < count; i++) {
            password[index++] = randomChar(characters);
        }
        return index;
    }

    private char randomChar(String characters) {
        return characters.charAt(random.nextInt(characters.length()));
    }
}
