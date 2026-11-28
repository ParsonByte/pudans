package cn.tolim.pudans.iam.infra.security.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

public class AuthenticationProvider implements Serializable {

    public static final AuthenticationProvider USERNAME_PASSWORD = new AuthenticationProvider(
            "username_password", "用户名密码认证");
    private static final long serialVersionUID = 1L;
    private final String type;
    private final String name;

    public AuthenticationProvider(String type, String name) {
        this.type = type;
        this.name = name;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthenticationProvider provider)) {
            return false;
        }
        return Objects.equals(type, provider.type) && Objects.equals(name, provider.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }
}
