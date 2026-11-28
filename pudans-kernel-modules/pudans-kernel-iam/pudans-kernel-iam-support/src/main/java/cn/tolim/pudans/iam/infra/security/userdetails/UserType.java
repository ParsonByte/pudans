package cn.tolim.pudans.iam.infra.security.userdetails;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

public class UserType implements Serializable {

    public static final UserType USER = new UserType("user", "用户");
    public static final UserType ADMIN = new UserType("admin", "管理员");
    private static final long serialVersionUID = 1L;
    private String type;
    private String name;

    public UserType() {
    }

    public UserType(String type, String name) {
        this.type = type;
        this.name = name;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserType userType)) {
            return false;
        }
        return Objects.equals(type, userType.type) && Objects.equals(name, userType.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }
}
