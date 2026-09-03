package cn.tolim.pudans.infra.utils;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;
import ua_parser.Client;
import ua_parser.Parser;


public enum UserAgentUtils {
    ;

    private static final Parser PARSER;

    static {
        var regexesResource = new ClassPathResource("config/uap/regexes.yaml", UserAgentUtils.class.getClassLoader());
        try {
            PARSER = new Parser(regexesResource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load regexes.yaml during UserAgent Parser initialization", e);
        }
    }


    public static Client parse(String agentString) {
        Assert.hasText(agentString, "User-Agent header must not be blank");
        return PARSER.parse(agentString);
    }
}
