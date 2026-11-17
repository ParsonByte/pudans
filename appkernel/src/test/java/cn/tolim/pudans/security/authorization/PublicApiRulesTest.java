package cn.tolim.pudans.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

class PublicApiRulesTest {

    @Test
    void publicRulesCoverRefreshAndAvatars() throws Exception {
        try (InputStream input =
                new ClassPathResource("extensions/role-template-public-apis.yaml").getInputStream()) {
            var document = (Map<String, Object>) new Yaml().load(input);
            var rules = (List<Map<String, Object>>) document.get("rules");
            var resources = rules.stream()
                    .map(rule -> (List<String>) rule.get("resources"))
                    .filter(java.util.Objects::nonNull)
                    .flatMap(List::stream)
                    .toList();

            assertThat(resources).contains("auth/refresh", "avatars", "captcha/challenge");
        }
    }
}
