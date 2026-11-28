package cn.tolim.pudans.iam.infra.security.userdetails;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

public class DataOrigin implements Serializable {

    public static final DataOrigin INPUT = new DataOrigin("input", "自建");
    public static final DataOrigin DING_TALK = new DataOrigin("dingtalk", "钉钉导入");
    public static final DataOrigin FEI_SHU = new DataOrigin("feishu", "飞书导入");
    private static final long serialVersionUID = 1L;
    private String type;
    private String name;

    public DataOrigin() {
    }

    public DataOrigin(String type, String name) {
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
        if (!(other instanceof DataOrigin dataOrigin)) {
            return false;
        }
        return Objects.equals(type, dataOrigin.type) && Objects.equals(name, dataOrigin.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }
}
