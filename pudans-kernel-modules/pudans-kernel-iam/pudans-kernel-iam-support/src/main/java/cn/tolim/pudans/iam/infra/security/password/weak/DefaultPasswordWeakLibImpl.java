package cn.tolim.pudans.iam.infra.security.password.weak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DefaultPasswordWeakLibImpl implements PasswordWeakLib {

    private static final Logger logger = LoggerFactory
            .getLogger(DefaultPasswordWeakLibImpl.class);
    private static final String DICTIONARY = "/dictionaries/10k-most-common.txt";
    private static final String[] DEFAULT_WORDS = {"password", "123456", "12345678",
            "qwerty", "123456789", "12345",
            "1234", "111111", "1234567", "dragon",
            "123123", "baseball", "abc123",
            "football", "monkey", "letmein",
            "696969", "shadow", "master",
            "666666", "qwertyuiop", "123321",
            "mustang", "1234567890", "michael",
            "654321", "superman", "1qaz2wsx",
            "7777777", "121212", "000000",
            "qazwsx", "123qwe", "killer",
            "trustno1", "jordan", "jennifer",
            "zxcvbnm", "asdfgh", "hunter",
            "buster", "soccer", "harley",
            "batman", "andrew", "tigger",
            "sunshine", "iloveyou", "2000",
            "charlie", "robert", "thomas",
            "hockey", "ranger", "daniel",
            "starwars", "klaster", "112233",
            "george", "computer", "michelle",
            "jessica", "pepper", "1111",
            "zxcvbn", "555555", "11111111",
            "131313", "freedom", "777777",
            "pass", "maggie", "159753",
            "aaaaaa", "ginger", "princess",
            "joshua", "cheese", "amanda",
            "summer", "love", "ashley",
            "nicole", "chelsea", "biteme",
            "matthew", "access", "yankees",
            "987654321", "dallas", "austin",
            "thunder", "taylor", "matrix"};
    private final Map<String, Boolean> words = new HashMap<>(16);

    public DefaultPasswordWeakLibImpl() {
        for (String word : DEFAULT_WORDS) {
            words.put(word, Boolean.TRUE);
        }
        loadDictionary();
    }

    @Override
    public Boolean wordExists(String word) {
        synchronized (words) {
            return Boolean.TRUE.equals(words.get(word));
        }
    }

    @Override
    public List<String> getWordList() {
        synchronized (words) {
            return List.copyOf(words.keySet());
        }
    }

    private void loadDictionary() {
        try (InputStream input = DefaultPasswordWeakLibImpl.class
                .getResourceAsStream(DICTIONARY)) {
            if (input == null) {
                logger.debug("弱密码字典 {} 不存在", DICTIONARY);
                return;
            }
            Set<String> loaded = new HashSet<>();
            Map<String, Boolean> entries = new HashMap<>(16);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        entries.put(line, Boolean.TRUE);
                        loaded.add(line);
                    }
                }
            }
            synchronized (words) {
                words.keySet().retainAll(loaded);
                words.putAll(entries);
            }
        } catch (IOException exception) {
            logger.error("无法加载弱密码字典 {}", DICTIONARY, exception);
        }
    }
}
